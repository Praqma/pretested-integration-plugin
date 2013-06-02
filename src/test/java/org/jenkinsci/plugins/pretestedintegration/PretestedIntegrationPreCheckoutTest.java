package org.jenkinsci.plugins.pretestedintegration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Scanner;

import static org.mockito.Mockito.*;

public class PretestedIntegrationPreCheckoutTest extends PretestedIntegrationTestCase {

	public void testShouldCreateInstance() throws Exception {
		Constructor <?> c = PretestedIntegrationPreCheckout.class.getConstructor(String.class);
		Object inst = c.newInstance("foo");
		
		assertNotNull(inst);
	}
	
	// public void testShouldReturnRepositoryUrl() throws Exception {
	// 	String repositoryUrl = "foo";
	// 	PretestedIntegrationPreCheckout instance = new PretestedIntegrationPreCheckout(repositoryUrl);
	// 	assertNotNull(instance);
	// 	assertEquals(instance.getStageRepository(), repositoryUrl);
	// }
	// 
	/**
	 * Tests the validateConfiguration method in the 
	 */
	// public void testShouldValidateConfiguration() throws Exception {
	// 	File tmp = setup(true, true);
	// 	
	// 	//Given a valid local repository
	// 	File stage = new File(tmp,"stage");
	// 	//When i test for validation
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	boolean isValid = descriptor.validateConfiguration(stage.getAbsolutePath());
	// 	//Then the validation method should return true
	// 	assertTrue(isValid);
	// 	
	// 	//Given a remote repository
	// 	//When i validate it
	// 	//Then the validation method should return true
	// 	//TODO: Make test cases with shh:// and http://
	// 	
	// }
	
	/**
	 * Test if the a new repository is correctly initialised
	 */
	// public void testShouldFailValidateConfiguration() throws Exception{
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	File tmp = getTempFile();
	// 	assertFalse(descriptor.validateConfiguration(tmp.getAbsolutePath()));
	// 	
	// 	//Given an empty directory, e.g. no .hg/
	// 	//When i test for validity
	// 	//Then the validation method should throw an InvalidRepositoryException exception
	// 	
	// 	//Given an empty valid repository e.g. hg init
	// 	//When i test for validity
	// 	//Then the validation method should return false
	// 	
	// 	//Given a repository without the hook
	// 	//When I test for validity
	// 	//Then the validation method should return false
	// 	
	// 	//Given a repository without the hgrc file
	// 	//When I test for validity
	// 	//Then the validation method should return false
	// 	
	// 	//Given a repository with a hgrc file
	// 	//And the changegroup hook does not exist
	// 	//Then the validation method should return false
	// }
	
	// public void testShouldGiveDotHgDirectory() throws Exception {
	// 	//Given a local filepath
	// 	String relativePath = "some/local/directory";
	// 	String relativePathEndingWithSlash = relativePath + "/";
	// 	//When I try to get the configuration directory for the repository
	// 	//Then the path is 
	// 	//PretestedIntegrationPreCheckout buildWrapper = new PretestedIntegrationPreCheckout("");
	// 
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//buildWrapper.getDescriptor();
	// 	
	// 	assertEquals(new File(relativePath + "/.hg"), descriptor.configurationDirectory(relativePath));
	// 	assertEquals(new File(relativePathEndingWithSlash + ".hg"), descriptor.configurationDirectory(relativePathEndingWithSlash));
	// }
	// 
	// public void testShouldSetupRepositoryDirectoryExists() throws Exception {
	// 	//Some initial setup
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Given a directory that does exist
	// 	File tmp = createTempDirectory();
	// 	//When I invoke the setup method
	// 	boolean setupResult = descriptor.setupRepositoryDirectory(tmp);
	// 	//Then the setup method should return true
	// 	assertTrue(setupResult);
	// 	//And the directory should still exist
	// 	assertTrue(tmp.exists());
	// }
	// 
	// public void testShouldSetupRepositoryDirectoryNotExists() throws Exception {
	// 	//Some initial setup
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Given a directory that does not exist
	// 	File tmp = getTempFile();
	// 	//When I invoke the setup method
	// 	boolean setupResult = descriptor.setupRepositoryDirectory(tmp);
	// 	//Then the setup method should return true
	// 	assertTrue(setupResult);
	// 	//And the directory should exist afterwards
	// 	assertTrue(tmp.exists());
	// 	
	// 	//cleanup the results
	// }
	// 
	// public void testShouldFailSetupDirectory() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	File tmp = createTempDirectory();
	// 	tmp.setWritable(false);
	// 	File newDir = new File(tmp,"foo");
	// 	
	// 	boolean setupResult = descriptor.setupRepositoryDirectory(newDir);
	// 	assertFalse(setupResult);
	// 	assertFalse(newDir.exists());
	// }
	// 
	// public void testShouldUpdateConfigurationHgrcNotExists() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Given that the repository is correctly setup
	// 	File tmp = getTempFile();
	// 	File repoDir = new File(tmp,".hg");
	// 	repoDir.mkdirs();
	// 	
	// 	File hgrc = new File(repoDir,"hgrc");
	// 	//And that the hgrc does not exist
	// 	assertFalse(hgrc.exists());
	// 	//When the the configuration is updated
	// 	boolean updateResult = descriptor.writeConfiguration(tmp.getAbsolutePath());
	// 	//And the configuration succeeds
	// 	assertTrue(updateResult);
	// 	//Then the hgrc file should exist
	// 	assertTrue(hgrc.exists());
	// 	//And the hgrc file should contain a new hook
	// 	Scanner scanner = new Scanner(hgrc);
	// 	assertNotNull(scanner.findWithinHorizon("[hooks]", 0));
	// 	assertNotNull(scanner.findWithinHorizon("changegroup = python:.hg/hg_changegroup_hook.py:run", 0));
	// }
	// 
	// public void testShouldUpdateConfigurationHgrcExists() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Setup the directory
	// 	File tmp = getTempFile();
	// 	File repoDir = new File(tmp,".hg");
	// 	repoDir.mkdirs();
	// 	
	// 	File hgrc = new File(repoDir,"hgrc");
	// 	assertTrue(hgrc.createNewFile());
	// 	boolean updateResult = descriptor.writeConfiguration(tmp.getAbsolutePath());
	// 	assertTrue(updateResult);
	// 	assertTrue(hgrc.exists());
	// 	Scanner scanner = new Scanner(hgrc);
	// 	assertNotNull(scanner.findWithinHorizon("[hooks]", 0));
	// 	assertNotNull(scanner.findWithinHorizon("changegroup = python:.hg/hg_changegroup_hook.py:run", 0));
	// }
	// 
	// public void testShouldFailUpdateConfigurationHgrcNotCreatable() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Setup the directory
	// 	File tmp = getTempFile();
	// 	File repoDir = new File(tmp,".hg");
	// 	repoDir.mkdirs();
	// 	repoDir.setWritable(false);
	// 	
	// 	File hgrc = new File(repoDir,"hgrc");
	// 	boolean updateResult = descriptor.writeConfiguration(tmp.getAbsolutePath());
	// 	assertFalse(updateResult);
	// 	assertFalse(hgrc.exists());
	// 	repoDir.setWritable(true);
	// }
	// 
	// public void testShouldFailUpdateConfigurationHgrcNotWritable() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	
	// 	//Setup the directory
	// 	File tmp = getTempFile();
	// 	File repoDir = new File(tmp,".hg");
	// 	repoDir.mkdirs();
	// 	
	// 	File hgrc = new File(repoDir,"hgrc");
	// 	hgrc.createNewFile();
	// 	hgrc.setWritable(false);
	// 	boolean updateResult = descriptor.writeConfiguration(tmp.getAbsolutePath());
	// 	assertFalse(updateResult);
	// 	hgrc.setWritable(true);
	// 	
	// }
	// 
	// public void testShouldUpdateHook() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = new PretestedIntegrationPreCheckout.DescriptorImpl();
	// 	File tmp = createTempDirectory();
	// 	boolean updateResult = descriptor.writeHook(tmp, "localhost:8080", "foo");
	// 	assertTrue(updateResult);
	// 	//File repoDir = new File(tmp,".hg");
	// 	File hook = new File(tmp, "hg_changegroup_hook.py");
	// 	assertTrue(hook.exists());
	// 	//Check stuff about the contents of the hook file maybe?
	// }
	// 
	// public void testShouldFailUpdateHookDirNotExists() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = spy(new PretestedIntegrationPreCheckout.DescriptorImpl());
	// 	
	// 	File tmp = getTempFile();
	// 	
	// 	try{
	// 		boolean updateResult = descriptor.writeHook(tmp, "localhost:8080", "foo");
	// 		fail("IOException expected. updateResult: " + updateResult);
	// 	} catch (IOException e){
	// 		
	// 	}
	// 	
	// 	File hook = new File(tmp, "hg_changegroup_hook.py"); 
	// 	assertFalse(hook.exists());
	// 	
	// }
	// 
	// public void testShouldFailUpdateHookDirNotWritable() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = spy(new PretestedIntegrationPreCheckout.DescriptorImpl());
	// 	
	// 	File tmp = getTempFile();
	// 	tmp.mkdirs();
	// 	tmp.setWritable(false);
	// 	
	// 	try{
	// 		boolean updateResult = descriptor.writeHook(tmp, "localhost:8080", "foo");
	// 		fail("IOException expected. updateResult: " + updateResult);
	// 	} catch (IOException e){
	// 		
	// 	}
	// 	
	// 	File hook = new File(tmp, "hg_changegroup_hook.py"); 
	// 	assertFalse(hook.exists());
	// 	tmp.setWritable(true);
	// }
	// 
	// public void testShouldFailUpdateHookFileNotWritable() throws Exception {
	// 	PretestedIntegrationPreCheckout.DescriptorImpl descriptor = spy(new PretestedIntegrationPreCheckout.DescriptorImpl());
	// 	
	// 	File tmp = getTempFile();
	// 	tmp.mkdirs();
	// 	File hook = new File(tmp, "hg_changegroup_hook.py"); 
	// 	
	// 	hook.setWritable(false);
	// 	
	// 	try{
	// 		boolean updateResult = descriptor.writeHook(hook, "localhost:8080", "foo");
	// 		fail("IOException expected. updateResult: " + updateResult);
	// 	} catch (IOException e){
	// 		
	// 	}
	// 	
	// 	assertFalse(hook.exists());
	// 	hook.setWritable(true);
	// }
	// 
	/*
	public void testShouldConfigureStageRepository() throws Exception {
		File tmp = setup();
		//Given a valid hg directory
			String path = tmp.getAbsolutePath();
			hg("init", path);
			//When the doUpdateRepository method is invoked
			PretestedIntegrationPreCheckout buildWrapper = new PretestedIntegrationPreCheckout(tmp.getAbsolutePath());
			PretestedIntegrationPreCheckout.DescriptorImpl mockedDescriptor = mock(PretestedIntegrationPreCheckout.DescriptorImpl.class);
			
			//		(PretestedIntegrationPreCheckout.DescriptorImpl) buildWrapper.getDescriptor();
			when(mockedDescriptor.getJenkinsRootUrl()).thenReturn("localhost:8080");
			FormValidation response = mockedDescriptor.doUpdateRepository(tmp.getAbsolutePath(), "");
			//Then a valid staging repository should be configured
			assertEquals(mockedDescriptor.getJenkinsRootUrl(), "localhost:8080");
			
			assertNotNull();
			//assertSame(response.kind,FormValidation.Kind.OK);
			assertTrue(mockedDescriptor.validateConfiguration(path));
			//Todo - check the actual state
		
	}*/
}
