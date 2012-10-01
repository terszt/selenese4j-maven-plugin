/**
 * 
 */
package com.github.raphc.maven.plugins.selenese4j.transform;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Raphael
 *
 */
public class DefaultMethodReader extends AbstractMethodReader {
    
	private Logger logger = Logger.getLogger(getClass().getName());
	
	/**
	 * 
	 * @param templatesDirectoryPath
	 * @param testBuildDirectory
	 */
	public DefaultMethodReader(String templatesDirectoryPath, String testBuildDirectory){
		this.templatesDirectoryPath = templatesDirectoryPath;
		this.testBuildDirectory = testBuildDirectory;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.github.raphc.maven.plugins.selenese4j.transform.IMethodReader#writeSource(java.io.File, com.github.raphc.maven.plugins.selenese4j.transform.ClassInfo, com.github.raphc.maven.plugins.selenese4j.transform.ScenarioTokens)
	 */
	public void writeSource(File dir, ClassInfo classBean, ScenarioTokens tokens) throws Exception {
	      
	      String[] javaSourcePackageDirs = classBean.getPackageName().split("\\.");
	      String javaSourceDirName = "";
	      for (String elt : javaSourcePackageDirs) {
	    	  javaSourceDirName = javaSourceDirName + File.separator + elt;
	      }
	      
	      File packageDir = new File(testBuildDirectory + File.separator + javaSourceDirName + File.separator);
	      packageDir.mkdirs();
	      
	      ScenarioConverter t = getScenarioConverter();
	      String javaSourceFile = packageDir.getAbsolutePath() + File.separator + classBean.getClassName() + ".java";
	      logger.log(Level.FINE, "Writing Java source  " + javaSourceFile + " ...");
	      t.doWrite(classBean, tokens, javaSourceFile);
    
	}
	  
}
