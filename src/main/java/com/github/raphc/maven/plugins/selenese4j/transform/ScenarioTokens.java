package com.github.raphc.maven.plugins.selenese4j.transform;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * 
 * @author Raphael
 * Hold all of the token used by the template or byt the scenario files
 */
class ScenarioTokens {

	/**
	 * The suite context
	 */
  private Map<String, String> suiteContext = new HashMap<String, String>();
  
  /**
   * The substitue type token list 
   */
  private Map<String, String> substituteEntries = new HashMap<String, String>();
  
  /**
   * The template type token list
   */
  private Map<String, String> templateEntries = new HashMap<String, String>();

  /**
   * 
   * @return
   */
  public Map<String, String> getSuiteContext() {
    return suiteContext;
  }

  /**
   * 
   * @param suiteContext
   */
  public void setSuiteContext(Map<String, String> suiteContext) {
    this.suiteContext = suiteContext;
  }

  /**
   * 
   * @return
   */
  public Map<String, String> getSubstituteEntries() {
    return substituteEntries;
  }

  /**
   * 
   * @param substituteEntries
   */
  public void setSubstituteEntries(Map<String, String> substituteEntries) {
    this.substituteEntries = substituteEntries;
  }

  /**
   * Adds a substitute token
   * @param key
   * @param value
   */
  public void addSubstituteEntry(String key, String value) {
    substituteEntries.put(key, value);
  }
  
  /**
   * Adds a template token
   * @param key
   * @param value
   */
  public void addTemplateEntry(String key, String value) {
	  templateEntries.put(key, value);
  }

  /**
   * Retourne la liste des cle de substitution stocke pour ce scenario
   * @param className
   * @return
   */
  public Map<String, String> getSubstituteEntries(String className) {
    Map<String, String> classSubstitutes = new HashMap<String, String>();
    for (String key : substituteEntries.keySet()) {
      if (key.startsWith(className + ".")) {
        classSubstitutes.put(key.replace(className + ".", ""), substituteEntries.get(key));
      }
    }
    return classSubstitutes;
  }

  /**
   * Retourne la liste des cle de template stocke pour ce scenario.
   * Si 2 cles sont trouvees (une liée à la classe de test et une autre globale),
   * C'est la valeur de la classe de tests qui sera associé à la clé
   * @param className
   * @return
   */
  public Map<String, String> getTemplateEntries(String className) {
    Map<String, String> classSubstitutes = new HashMap<String, String>();
    for (String key : templateEntries.keySet()) {
      if (key.startsWith(className + ".")) {
    	  classSubstitutes.put(key.replace(className + ".", ""), templateEntries.get(key));
      } else if(key.startsWith("global.")){
    	  String finalKey = key.replace("global.", "");
    	  if(! classSubstitutes.containsKey(finalKey)){
    		  classSubstitutes.put(finalKey, templateEntries.get(key));
    	  }
      }
    }
    return classSubstitutes;
  }
  
  /**
   * 
   * @param key
   * @param value
   */
  public void addToContext(String key, String value) {
    suiteContext.put(key, value);
  }

  /**
   * 
   * @param key
   * @return
   */
  public String getFromContext(String key) {
    return suiteContext.get(key);
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

}
