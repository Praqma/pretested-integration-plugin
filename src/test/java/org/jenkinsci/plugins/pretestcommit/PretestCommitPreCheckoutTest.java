package org.jenkinsci.plugins.pretestcommit;

import java.io.File;
import java.util.Scanner;

public class PretestCommitPreCheckoutTest extends PretestCommitTestCase {

	/**
	 * Tests the validateConfiguration method in the 
	 */
	public void testShouldValidateConfiguration() throws Exception{
		File tmp = setup(true, true);
		
		//Given a valid local repository
		File stage = new File(tmp,"stage");
		//When i test for validation
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		boolean isValid = descriptor.validateConfiguration(stage.getAbsolutePath());
		//Then the validation method should return true
		assertTrue(isValid);
		
		//Given a remote repository
		//When i validate it
		//Then the validation method should return true
		//TODO: Make test cases with shh:// and http://
		
	}
	
	/**
	 * Test if the a new repository is correctly initialised
	 */
	public void testShouldNotValidateConfiguration() throws Exception{
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		File tmp = getTempFile();
		assertFalse(descriptor.validateConfiguration(tmp.getAbsolutePath()));
		
		//Given an empty directory, e.g. no .hg/
		//When i test for validity
		//Then the validation method should throw an InvalidRepositoryException exception
		
		//Given an empty valid repository e.g. hg init
		//When i test for validity
		//Then the validation method should return false
		
		//Given a repository without the hook
		//When I test for validity
		//Then the validation method should return false
		
		//Given a repository without the hgrc file
		//When I test for validity
		//Then the validation method should return false
		
		//Given a repository with a hgrc file
		//And the changegroup hook does not exist
		//Then the validation method should return false
	}
	
	public void testShouldGiveDotHgDirectory() throws Exception {
		
		//Given a local filepath
		String relativePath = "some/local/directory";
		String relativePathEndingWithSlash = relativePath + "/";
		//When I try to get the configuration directory for the repository
		//Then the path is 
		//PretestCommitPreCheckout buildWrapper = new PretestCommitPreCheckout("");
	
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//buildWrapper.getDescriptor();
		
		assertEquals(new File(relativePath + "/.hg"), descriptor.configurationDirectory(relativePath));
		assertEquals(new File(relativePathEndingWithSlash + ".hg"), descriptor.configurationDirectory(relativePathEndingWithSlash));
	}
	
	public void testShouldSetupRepositoryDirectory() throws Exception {
		
		//Some initial setup
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//Given a directory that does not exist
		File tmp = getTempFile();
		//When I invoke the setup method
		boolean setupResult = descriptor.setupRepositoryDirectory(tmp);
		//Then the setup method should return true
		assertTrue(setupResult);
		//And the directory should exist afterwards
		assertTrue(tmp.exists());
		
		//cleanup the results
		tmp = null;
		
		//Given a directory that does exist
		tmp = createTempDirectory();
		//When I invoke the setup method
		setupResult = descriptor.setupRepositoryDirectory(tmp);
		//Then the setup method should return true
		assertTrue(setupResult);
		//And the directory should still exist
		assertTrue(tmp.exists());
	}
	
	public void testShouldUpdateConfigurationHgrcNotExists() throws Exception {
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//Given that the repository is correctly setup
		File tmp = getTempFile();
		File repoDir = new File(tmp,".hg");
		repoDir.mkdirs();
		
		File hgrc = new File(repoDir,"hgrc");
		//And that the hgrc does not exist
		assertFalse(hgrc.exists());
		//When the the configuration is updated
		boolean updateResult = descriptor.updateConfiguration(tmp.getAbsolutePath());
		//And the configuration succeeds
		assertTrue(updateResult);
		//Then the hgrc file should exist
		assertTrue(hgrc.exists());
		//And the hgrc file should contain a new hook
		Scanner scanner = new Scanner(hgrc);
		assertNotNull(scanner.findWithinHorizon("[hooks]", 0));
		assertNotNull(scanner.findWithinHorizon("changegroup = python:.hg/hg_changegroup_hook.py:run", 0));
	}
	
	public void testShouldUpdateConfigurationHgrcExists() throws Exception {
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//Setup the directory
		File tmp = getTempFile();
		File repoDir = new File(tmp,".hg");
		repoDir.mkdirs();
		
		File hgrc = new File(repoDir,"hgrc");
		assertTrue(hgrc.createNewFile());
		boolean updateResult = descriptor.updateConfiguration(tmp.getAbsolutePath());
		assertTrue(updateResult);
		assertTrue(hgrc.exists());
		Scanner scanner = new Scanner(hgrc);
		assertNotNull(scanner.findWithinHorizon("[hooks]", 0));
		assertNotNull(scanner.findWithinHorizon("changegroup = python:.hg/hg_changegroup_hook.py:run", 0));
	}
	
	public void testShouldFailUpdateConfigurationHgrcNotCreatable() throws Exception {
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//Setup the directory
		File tmp = getTempFile();
		File repoDir = new File(tmp,".hg");
		repoDir.mkdirs();
		repoDir.setWritable(false);
		
		File hgrc = new File(repoDir,"hgrc");
		boolean updateResult = descriptor.updateConfiguration(tmp.getAbsolutePath());
		assertFalse(updateResult);
		assertFalse(hgrc.exists());
		repoDir.setWritable(true);
	}
	
	public void testShouldFailUpdateConfigurationHgrcNotWritable() throws Exception {
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl();
		
		//Setup the directory
		File tmp = getTempFile();
		File repoDir = new File(tmp,".hg");
		repoDir.mkdirs();
		
		File hgrc = new File(repoDir,"hgrc");
		hgrc.createNewFile();
		hgrc.setWritable(false);
		boolean updateResult = descriptor.updateConfiguration(tmp.getAbsolutePath());
		assertFalse(updateResult);
		hgrc.setWritable(true);
		
	}
	
	public void testShouldUpdateHook() throws Exception {
		PretestCommitPreCheckout.DescriptorImpl descriptor = new PretestCommitPreCheckout.DescriptorImpl() {
			@Override
			public String getJenkinsRootUrl() {
				return "localhost:8080";
			}
			
		};
		
		File tmp = getTempFile();
		descriptor.updateHook(tmp.getAbsolutePath(), "foo");
		File repoDir = new File(tmp,".hg");
		File hook = new File(repoDir, "hg_changegroup_hook.py");
		assertTrue(hook.exists());
		//Check stuff about the contents of the hook file maybe?
	}
	
	/*
	public void testShouldConfigureStageRepository() throws Exception {
		File tmp = setup();
		//Given a valid hg directory
			String path = tmp.getAbsolutePath();
			hg("init", path);
			//When the doUpdateRepository method is invoked
			PretestCommitPreCheckout buildWrapper = new PretestCommitPreCheckout(tmp.getAbsolutePath());
			PretestCommitPreCheckout.DescriptorImpl mockedDescriptor = mock(PretestCommitPreCheckout.DescriptorImpl.class);
			
			//		(PretestCommitPreCheckout.DescriptorImpl) buildWrapper.getDescriptor();
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
