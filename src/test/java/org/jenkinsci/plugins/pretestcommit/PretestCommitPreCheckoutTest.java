package org.jenkinsci.plugins.pretestcommit;

import hudson.util.FormValidation;

import java.io.File;

public class PretestCommitPreCheckoutTest extends PretestCommitTestCase {

	/**
	 * Tests the validateConfiguration method in the 
	 */
	public void testShouldValidateConfiguration() throws Exception{
		File tmp = setup(true, true);
		
		//Given a valid local repository
		File stage = new File(tmp,"stage");
		//When i test for validation
		PretestCommitPreCheckout.DescriptorImpl descriptor = 
				(PretestCommitPreCheckout.DescriptorImpl) buildWrapper.getDescriptor();
		boolean isValid = descriptor.validateConfiguration(stage.getAbsolutePath());
		//Then the validation method should return true
		assertTrue(isValid);
		
		//Given a remote repository
		//When i validate it
		//Then the validation method should return true
		//TODO: Make test cases with shh:// and http://
		
	}
	
	public void testShouldSetupDirectory() throws Exception {
		
		//Some initial setup
		PretestCommitPreCheckout obj = new PretestCommitPreCheckout("");
		PretestCommitPreCheckout.DescriptorImpl descriptor = 
				(PretestCommitPreCheckout.DescriptorImpl) obj.getDescriptor();
		
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
	
	/**
	 * Test if the a new repository is correctly initialised
	 */
	public void testShouldNotValidateConfiguration() throws Exception{
		
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
	
	public void testShouldConfigureStageRepository() throws Exception {
		File tmp = setup();
		//Given a valid hg directory
			String path = tmp.getAbsolutePath();
			hg("init", path);
			//When the doUpdateRepository method is invoked
			PretestCommitPreCheckout buildWrapper = new PretestCommitPreCheckout(tmp.getAbsolutePath());
			PretestCommitPreCheckout.DescriptorImpl descriptor = 
					(PretestCommitPreCheckout.DescriptorImpl) buildWrapper.getDescriptor();
			FormValidation response = descriptor.doUpdateRepository(tmp.getAbsolutePath(), "");
			//Then a valid staging repository should be configured
			
			assertSame(response.kind,FormValidation.Kind.OK);
			assertTrue(descriptor.validateConfiguration(path));
			//Todo - check the actual state
		
	}
}
