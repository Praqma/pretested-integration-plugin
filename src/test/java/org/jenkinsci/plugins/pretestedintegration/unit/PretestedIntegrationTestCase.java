package org.jenkinsci.plugins.pretestedintegration.unit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import hudson.util.StreamTaskListener;

import org.junit.*;

/**
 * Base class for testing classes that requires a jenkins instance. This class 
 * should only contain methods that are used across testcases.
 * @author rel
 *
 */
public abstract class PretestedIntegrationTestCase extends HudsonTestCase {
	
	public FreeStyleProject project;
	public BuildWrapper buildWrapper;
	
	public String jobName = "test";
	public String jenkinsRoot = "localhost:8080";
	
	public TaskListener listener;
	public Launcher launcher;
	

	 public void setup() throws Exception, IOException {
		 listener = new StreamTaskListener(System.out, Charset.defaultCharset());
		 launcher = Hudson.getInstance().createLauncher(listener);
	 }
	 

	 	public static File getTempFile() 
	 			throws IOException {
	 		final File temp = File.createTempFile("prteco-"+ Long.toString(System.nanoTime()),"");
	 	    
	 	    if(!(temp.delete()))
	 	    {
	 	        throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
	 	    }
	 	    
	 	    return temp;
	 	}
	 	/**
	 	 * Borrowed from
	 	 * http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
	 	 * potentially unsafe, but we only use it for testing, so it suffice.
	 	 * @return A filepointer to a temporary directory
	 	 */
	 	public static File createTempDirectory()
	 		    throws IOException
	 		{
	 		    final File temp = getTempFile();
	 		   
	 		    if(!(temp.mkdir()))
	 		    {
	 		        throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
	 		    }
	 		    return (temp);
	 		}
		public static Object genericTestConstructor(final Class<?> cls) 
				   throws InstantiationException, IllegalAccessException, 
				InvocationTargetException 
				   { 
				      final Constructor<?> c = cls.getDeclaredConstructors()[0]; 
				      c.setAccessible(true); 
				      final Object n = c.newInstance((Object[])null); 
				      
				      Assert.assertNotNull(n); 
				      return n; 
				   }   
}
