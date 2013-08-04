package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class MercurialIT extends MercurialTestCase {
	
	/**
	 * Test that proper workflow with two development branches work
	 */
	public void testProperWorkflow() throws Exception {
		//initialise remote
		setup();
		File remote = createTempDirectory();
		hg(remote,"init");
		File foo = new File(remote,"foo");
		FileUtils.touch(foo);
		hg(remote,"add","foo");

		File bar = new File(remote,"bar");
		FileUtils.touch(bar);
		hg(remote,"add","bar");
		hg(remote,"commit","-m","Initial import");
		
		//setup and configure 2 jobs for different branches with pretested integration
		FreeStyleProject alice = createJob(remote.getAbsolutePath(),"alice","alice","default");
		FreeStyleProject bob = createJob(remote.getAbsolutePath(),"bob","bob","default");
				
		//work on one branch
		hg(remote,"branch","alice");
		FileUtils.writeStringToFile(foo, "hello, world!");
		hg(remote,"commit","-m","Added foo");
		String aliceRevision = hg(remote,"tip","--template","{node}").toString();
		
		//work on the other
		hg(remote,"branch","bob");
		FileUtils.writeStringToFile(bar, "hello, world!");
		hg(remote,"commit","-m","Added bar");
		String bobRevision = hg(remote,"tip","--template","{node}").toString();
		
		//trigger jenkins build
		
		alice.scheduleBuild2(0).get();
		bob.scheduleBuild2(0).get();
	
		//the jobs should now be present in the integration branch merged in the same order as comitted.
		ByteArrayOutputStream revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		String[] revArr = revisions.toString().split("\n");
		//The first element is the initial commit
		assertTrue(revArr[1].startsWith("Merge of revision " + aliceRevision));
		assertTrue(revArr[2].startsWith("Merge of revision " + bobRevision));
		
		//now alice can work on bobs changes
		hg(remote,"update","alice");
		hg(remote,"merge","default");
		hg(remote,"commit","-m","Synched with default");
		String aliceRevision2 = hg(remote,"tip","--template","{node}").toString();
		alice.scheduleBuild2(0).get();
		FileUtils.writeStringToFile(bar, "hello, world from bar");
		hg(remote,"commit","-m","Added sender in bar");
		String aliceRevision3 =  hg(remote,"tip","--template","{node}").toString();
		alice.scheduleBuild2(0).get();
		
		revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		revArr = revisions.toString().split("\n");
		assertTrue(revArr[3].startsWith("Merge of revision " + aliceRevision2));
		assertTrue(revArr[4].startsWith("Merge of revision " + aliceRevision3));
		
		//bob can start on a new thing without checking out the integration branch
		hg(remote,"update","bob");
		File baz = new File(remote,"baz");
		FileUtils.writeStringToFile(baz, "This is a new feature in baz");
		hg(remote,"add","baz");
		hg(remote,"commit","-m","Started feature in baz");
		String bobRevision2 = hg(remote,"tip","--template","{node}").toString();
		bob.scheduleBuild2(0).get();

		revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		revArr = revisions.toString().split("\n");
		assertTrue(revArr[5].startsWith("Merge of revision " + bobRevision2));
	}
	
	public void testProperWorkflowReversed() throws Exception {
		//do the same as testProperWorkflow but commit in reversed order
	}
	
	/**
	 * Test that a rejected commit does not ruin the integration branch
	 */
	
	public void testRejectedCommit() throws Exception {
		//initialise remote
		setup();
		File remote = createTempDirectory();
		hg(remote,"init");
		
		//setup and configure 2 jobs for different branches with pretested integration
		FreeStyleProject alice = createJob(remote.getAbsolutePath(),"alice","alice","default");
		FreeStyleProject bob = createJob(remote.getAbsolutePath(),"bob","bob","default");
		
		//work on one branch
		hg(remote,"branch","alice");
		File foo = new File(remote,"foo");
		FileUtils.writeStringToFile(foo, "print \"hello, world!\"");
		hg(remote,"add","foo");
		hg(remote,"commit","-m","Added foo.py");
		
		//work on the other
		hg(remote,"branch","bob");
		File bar = new File(remote,"bar");
		FileUtils.writeStringToFile(bar, "print \"hello, world!\"");
		hg(remote,"add","bar");
		hg(remote,"commit","-m","Added bar.py");
		
		//trigger jenkins build
		alice.scheduleBuild2(0);
		bob.scheduleBuild2(0);
		
		//Only the first branch should be built
		
		//commense more work on the first branch
		//commit
		
		//fix the issue on the second branch
		//commit
		
		//trigger jenkins build
		//The second job should now be built as well as the first
	}
	
	/**
	 * Test that a reset causes the same order of verification
	 */
	
	public void testReset() throws Exception {
		//do some work on a branch that succeeds
		
		//now do some work where the build starts failing, but the work is proper
		
		//reset the plugin
		//trigger a new build and see that everything succeeds
		
	}
	
	/**
	 * Test that if a build has succeeded, even though the 
	 */
	public void testResetAfterIntegration() throws Exception {
		
	}
	
	/* Utility methods */
	
	public FreeStyleProject createJob(String source, String name, String developmentBranch, String integrationBranch) throws IOException {
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, name);
		MercurialSCM scm = new MercurialSCM(null,source,developmentBranch,null,null,null, true);
		project.setScm(scm);
		
		Mercurial mercurial = new Mercurial(false, integrationBranch);
		PretestedIntegrationBuildWrapper buildWrapper = new PretestedIntegrationBuildWrapper(mercurial);
		project.getBuildWrappersList().add(buildWrapper);
		return project;
	}
	
	public String latestRevision(File repo) throws Exception {
		return hg(repo,"tip","--template","{node}").toString();
	}
} 
