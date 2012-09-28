/**
 * 
 */
package com.github.raphc.maven.plugins.selenese4j.transform;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.LocaleUtils;
import org.apache.commons.lang.StringUtils;

import com.github.raphc.maven.plugins.selenese4j.Selenese4JProperties;
import com.github.raphc.maven.plugins.selenese4j.source.data.Html;
import com.github.raphc.maven.plugins.selenese4j.xstream.converter.TdContentConverter;
import com.thoughtworks.xstream.XStream;

/**
 * @author Raphael
 *
 */
public class SourceGenerator implements ISourceGenerator {

	private Logger logger = Logger.getLogger(getClass().getName());
	
	private Locale i18nMessagesLocale = GeneratorConfiguration.DEFAULT_I18N_MESSAGES_LOCALE;
	
	/**
	 * traducteur
	 * @component role="org.rcr.maven.selenese4j.transform.ICommandToMethodTranslator"
	 */
	private ICommandToMethodTranslator commandToMethodTranslator;
	
	private static XStream xstream;
	
	public SourceGenerator(){
		//Initialize XStream
		xstream = new XStream();
		xstream.autodetectAnnotations(true);
		xstream.processAnnotations(Html.class);
		xstream.registerConverter(new TdContentConverter());
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.rcr.maven.selenese4j.transform.ISourceGenerator#generate(java.io.File, org.rcr.maven.selenese4j.transform.IMethodReader)
	 */
	public void generate(File scenariiRootDirectory, IMethodReader methodReader) throws Exception {
		
		FileFilter dirFilter = new FileFilter() {
		      public boolean accept(File file) {
		        return file.isDirectory() && ! ArrayUtils.contains(GeneratorConfiguration.EXCLUDED_TEST_RESOURCES_DIR, file.getName());
		      }
		    };
		
		File[] suiteDirs = scenariiRootDirectory.listFiles(dirFilter);
		
		if(ArrayUtils.isEmpty(suiteDirs)){
			logger.log(Level.WARNING, "No suite directories found into ["+scenariiRootDirectory.getName()+"] !!!!");
			return;
		}
		
		for (File suiteDir : suiteDirs) {
			logger.info("reading file ["+suiteDir+"]...");
			processTests(suiteDir, methodReader, null);
		}

	}
	
	/**
	 * 
	 * @param overrideTemplatesDirectoryPath
	 * @param buildDir
	 * @param dir
	 * @param methodReader
	 * @param tokens
	 * @throws Exception
	 */
	private void processTests(File dir, IMethodReader methodReader, ScenarioTokens tokens) throws Exception {
		logger.log(Level.FINE, "Reading " + dir + " tests...");
		ScenarioTokens scenarioTokens = new ScenarioTokens();
		if (tokens != null) {
			scenarioTokens.setSubstituteEntries(tokens.getSubstituteEntries());
			scenarioTokens.setSuiteContext(tokens.getSuiteContext());
		}

		File suiteFile = new File(dir, GeneratorConfiguration.SUITE_FILE_NAME);
		if (!suiteFile.exists()) {
			throw new RuntimeException("Missing \""+GeneratorConfiguration.SUITE_FILE_NAME+"\" file at " + dir + ".");
		}
		Collection<File> files = ScenarioHtmlParser.parseSuite(suiteFile);
		Collection<String> classBeans = new ArrayList<String>();

		//On definit le nom des packages
		File propFile = new File(dir, Selenese4JProperties.GLOBAL_CONF_FILE_NAME);
		Properties properties = new Properties();
		String basedPackageName = null;
		try {
			properties.load(new FileInputStream(propFile));
			basedPackageName = properties.getProperty(GeneratorConfiguration.PROP_BASED_TESTS_SOURCES_PACKAGE);
			logger.log(Level.FINE, "Property ["+GeneratorConfiguration.PROP_BASED_TESTS_SOURCES_PACKAGE+"] loaded ["+basedPackageName+"].");
		} catch (FileNotFoundException e1) {
			throw new RuntimeException("Missing \"" + Selenese4JProperties.GLOBAL_CONF_FILE_NAME + "\" file at " + dir + ".");
		}
		
		String packName = null;
		packName = basedPackageName != null ? basedPackageName + "." + dir.getName() : dir.getName();
		loadContextKeys(dir, scenarioTokens);

		for (File file : files) {
			
			String scenariiGenerationDisabledValue = properties.getProperty("scenarii.generation.disabled");
			if(StringUtils.contains(scenariiGenerationDisabledValue, file.getName())){
				logger.log(Level.FINE, "The conversion of the following scenario file has been disabled [" + file.getName() + "]. Conversion skipped.");
				continue;
			}
			
			logger.log(Level.FINE, "Processing [" + file.getName() + "]...");
			StringBuilder sb = new StringBuilder();
			String className = StringUtils.chomp(file.getName(), ".").concat(GeneratorConfiguration.GENERATED_JAVA_TEST_CLASS_SUFFIX);
			// Parsing du fichier. On extrait les commandes
			Html html = (Html) xstream.fromXML(file);
			logger.log(Level.FINE, "Html Parsing result is [" + html + "]. ["+CollectionUtils.size(html.getBody().getTable().getTbody().getTrs())+"] lines found.");
			if(html.getBody().getTable().getTbody().getTrs() == null){
				logger.log(Level.SEVERE, "No lines extracted from html ["+file.getName()+"]");
				return;
			}
			Collection<Command> cmds = HtmlConverter.convert(html.getBody().getTable().getTbody().getTrs());
			
			//Traduction des commandes en instruction java
			for (Command c : cmds) {
				String cmdStr = commandToMethodTranslator.discovery(c);
				cmdStr = populatingCommand(dir, className, cmdStr, scenarioTokens);
				sb.append("\n\t\t" + cmdStr);
			}
			
			logger.log(Level.FINE, "Generating ["+packName+"]["+className+"] ...");
			
			ClassInfo classInfo = new ClassInfo();
			classInfo.setPackageName(packName);
			classInfo.setClassName(className);
			classInfo.setMethodBody(sb.toString());
			writeTestFile(dir, methodReader, classInfo, scenarioTokens, classBeans);
			
		}

		// Generation de la classe annot� @Suite
		//base sur l'ordre de presentation dans le fichier suite.html
		createOrderedSuite(methodReader.getTemplatesDirectoryPath(), methodReader.getTestBuildDirectory(), classBeans, scenarioTokens, packName, dir.getName());
	}
	
	/**
	 * Remplace les tokens ${xxxxx} par une possible cl� de substitution 
	 * presente <i>substitute.yyyyyyy.xxxxxx</i> dans le fichier selenium4j o� yyyyyyy correspond au nom du testcase YYYYYTestCase.
	 * Remplace les tokens ${messages.xxxxx} par un messages i18n correspond � la langue fournie.
	 * Par defaut, la recherche est realise en ${@link GeneratorConfiguration#DEFAULT_I18N_MESSAGES_LOCALE}.
	 * Si absent aucun modification n'est faite.
	 * @param basedir
	 * @param className
	 * @param cmdStr
	 * @param scenarioTokens
	 * @return
	 */
	private String populatingCommand(File basedir, String className, String cmdStr, ScenarioTokens scenarioTokens) {
		String newCmdStr = cmdStr;
		
		Map<String, String> subEntries = scenarioTokens.getSubstituteEntries(className);
		
		//Application des cl�s de subsitution
		logger.log(Level.FINE, "Populating cmd ["+cmdStr+"] with substitue keys of ["+className+"] ("+CollectionUtils.size(subEntries)+" keys) ...");
		
		for (String key : subEntries.keySet()) {
			if (cmdStr.contains("${" + key + "}")) {
				String ek = subEntries.get(key);
				logger.log(Level.FINE, "Replacing string [${" + key + "}] in cmd ["+cmdStr+"] by [" + ek + "].");
				newCmdStr = cmdStr.replace("${" + key + "}", ek);
			}
		}
		
		//Application du bundling i18n
		try {
			URL[] urls = new URL[]{basedir.toURI().toURL()};
			URLClassLoader loader = new URLClassLoader(urls);
			ResourceBundle resource = ResourceBundle.getBundle(GeneratorConfiguration.I18N_MESSAGES_FILE_BASENAME, i18nMessagesLocale, loader);
			
			Pattern i18nTokensPattern = Pattern.compile("[.\\s]*(\\$\\{" + GeneratorConfiguration.SOURCE_FILE_I18N_TOKENS_PREFIX + "\\.([\\S&&[^$]]*)\\}(\\[.*\\])?)+[.\\s]*");
			Matcher matcher = i18nTokensPattern.matcher(newCmdStr);
			while(matcher.find()){
				String i18nTokenKey = matcher.group(2);
				
				String[] i18nTokenValueTokens = new String[0];
				if(StringUtils.isNotBlank(matcher.group(3))){
					i18nTokenValueTokens = StringUtils.split(matcher.group(3), ',');
				}
				
				logger.log(Level.FINE, "Found i18n token [" +i18nTokenKey+ "] [" +matcher.group(3)+ "]("+i18nTokenValueTokens.length+" elts) in cmd [" +newCmdStr+ "]");
				if(! resource.containsKey(i18nTokenKey)){
					logger.log(Level.FINE, "The matching token [" +i18nTokenKey+ "] has been not found in resource bundle [" +resource+"]. The conversion will be skipped.");
				} else {
					// On recupere la chaine initiale
					String message = resource.getString(i18nTokenKey);
					// On remplace les differentes variables par les tokens references
					// TODO String valuedMessage = MessageFormat.format(message, i18nTokenValueTokens);
					String valuedMessage = message;
					logger.log(Level.FINE, "Replacing string [${" + GeneratorConfiguration.SOURCE_FILE_I18N_TOKENS_PREFIX.concat(".").concat(i18nTokenKey) + "}] in cmd ["+newCmdStr+"] by [" + valuedMessage + "].");
					newCmdStr = newCmdStr.replace("${" + GeneratorConfiguration.SOURCE_FILE_I18N_TOKENS_PREFIX.concat(".").concat(i18nTokenKey) + "}", filter(valuedMessage));
				}
			}
		} catch(Exception e){
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		
		//Application des variables locales
		//Substitution basique : Doit prendre en compte le contexte du positionnement
		Pattern localVariableTokensPattern = Pattern.compile("[.\\s]*(\\$\\{local\\.variable\\.([\\S]*)\\})+[.\\s]*");
		Matcher matcher = localVariableTokensPattern.matcher(newCmdStr);
		while(matcher.find()){
			String localVariableToken = matcher.group(2);
			logger.log(Level.FINE, "Replacing java class local variable token [" +localVariableToken+ "] in cmd [" +newCmdStr+ "]");
			newCmdStr = newCmdStr.replace("${local.variable." + localVariableToken + "}", "\" + " +localVariableToken + " + \"\"");
		}
		
		return newCmdStr;
	}
	
	/**
	 * 
	 * @param dir
	 * @param scenarioTokens
	 * @throws Exception
	 */
	private void loadContextKeys(File dir, ScenarioTokens scenarioTokens) throws Exception {
		logger.log(Level.FINE, "Loading context for '" + dir + "'");
		File propFile = new File(dir, Selenese4JProperties.GLOBAL_CONF_FILE_NAME);
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(propFile));

			// load the context. entries
			for (Object key : properties.keySet()) {
				String key_ = (String) key;
				if (key_.startsWith("context.")) {
					String ctxKey = key_.replace("context.", "");
					scenarioTokens.addToContext(ctxKey, properties.getProperty(key_));
					logger.log(Level.FINE, "Context key [" + key_ + "] added as [" + ctxKey + "] [" + properties.getProperty(key_) + "] .");
				}
			}

			// load the html substitute. entries
			for (Object key : properties.keySet()) {
				String key_ = (String) key;
				if (key_.startsWith("substitute.")) {
					String ctxKey = key_.replace("substitute.", "");
					scenarioTokens.addSubstituteEntry(ctxKey, properties.getProperty(key_));
					logger.log(Level.FINE, "Substitute key [" + key_ + "] added as [" + ctxKey + "] [" + properties.getProperty(key_) + "] .");
				}
			}

			// load the dedicated template. velocity entries
			for (Object key : properties.keySet()) {
				String key_ = (String) key;
				if (key_.startsWith("template.")) {
					String ctxKey = key_.replace("template.", "");
					scenarioTokens.addTemplateEntry(ctxKey, properties.getProperty(key_));
					logger.log(Level.FINE, "Template key [" + key_ + "] added as [" + ctxKey + "] [" + properties.getProperty(key_)	+ "] .");
				}
			}

			// load global properties
			String definedLocale = properties.getProperty(GeneratorConfiguration.I18N_MESSAGES_LOCALE);
			if (StringUtils.isNotBlank(definedLocale)) {
				i18nMessagesLocale = LocaleUtils.toLocale(definedLocale);
			}

		} catch (FileNotFoundException e1) {
			logger.log(Level.SEVERE, "Missing \"" + Selenese4JProperties.GLOBAL_CONF_FILE_NAME + "\" file at " + dir + ".", e1);
			throw new RuntimeException("Missing \"" + Selenese4JProperties.GLOBAL_CONF_FILE_NAME + "\" file at " + dir + ".");
		}
	}
	
	/**
	 * 
	 * @param dir
	 * @param methodReader
	 * @param classInfo
	 * @param tokens
	 * @param classInfos
	 * @throws Exception
	 */
	private void writeTestFile(File dir, IMethodReader methodReader, ClassInfo classInfo, ScenarioTokens tokens, Collection<String> classInfos) throws Exception {
		methodReader.read(dir, classInfo, tokens);
		String classInfoCanonicalName = classInfo.getPackageName() + "." + classInfo.getClassName();
		classInfos.add(classInfoCanonicalName);
		logger.log(Level.FINE, "ClassInfo [" + classInfoCanonicalName + "] added.");
	}
	
	/**
	 * 
	 * @param classBeans
	 * @param tokens
	 * @param packageName
	 * @param dirName
	 */
	private void createOrderedSuite(String overrideTemplatesDirectoryPath, String buildDir, Collection<String> classBeans, ScenarioTokens tokens, String packageName, String dirName) {
		logger.log(Level.FINE, "Building Order Suite for " + dirName);
		VelocitySuiteTranslator t = getVelocitySuiteTranslator(overrideTemplatesDirectoryPath, GeneratorConfiguration.ORDERED_TESTS_SUITE_TEMPLATE_NAME);

		String[] packageDirs = packageName.split("\\.");
		String allDirName = "";
		for (String s : packageDirs) {
			allDirName = allDirName + File.separator + s;
		}

		File targetFile = new File(buildDir + allDirName);
		targetFile.mkdirs();

		String fileOut = buildDir + allDirName + File.separator + "OrderedTestSuite.java";
		t.doWrite(classBeans, tokens, packageName, fileOut, false, false);
	}

	/**
	 * Retourne une instance du traducteur Velocity
	 * 
	 * @return
	 */
	private VelocitySuiteTranslator getVelocitySuiteTranslator(String overrideTemplatesDirectoryPath, String templateFile) {
		VelocitySuiteTranslator out = null;

		String externalTemplateDir = StringUtils.substringAfter(overrideTemplatesDirectoryPath, GeneratorConfiguration.VELOCITY_FILE_LOADER + ":");
		if (StringUtils.isNotEmpty(externalTemplateDir)
				&& (new File(externalTemplateDir + File.separator + templateFile)).exists()) {
			out = new VelocitySuiteTranslator(GeneratorConfiguration.VELOCITY_FILE_LOADER, externalTemplateDir, templateFile);
		} else {
			out = new VelocitySuiteTranslator(GeneratorConfiguration.DEFAULT_VELOCITY_LOADER, GeneratorConfiguration.DEFAULT_TEMPLATE_DIRECTORY_PATH, templateFile);
		}
		return out;
	}
	
	/**
	 * 
	 */
	protected static String filter(String s) {
		if(s != null){
			s = s.replace("\\", "\\\\");
			s = s.replace("\"", "\\\"");
		}
		return s;
	}
	
}