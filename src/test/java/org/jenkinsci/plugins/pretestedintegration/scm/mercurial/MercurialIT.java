package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.plugins.mercurial.MercurialSCM;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jvnet.hudson.test.TestBuilder;

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
		
		Future<FreeStyleBuild> aliceBuild = alice.scheduleBuild2(0);
		Future<FreeStyleBuild> bobBuild = bob.scheduleBuild2(0);
		
		aliceBuild.get();
		bobBuild.get();
	
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

	/**
	 * Test that a merge fail commit can be fixed and merged
	 */
	public void testFailedMergeIntegration() throws Exception {
		//Create a commit that cannot be merged
		//Resolve the merge issues (by synching the integration branch)
		//now the commit should be integrated
		setup();
		File remote = createTempDirectory();
		hg(remote,"init");
		File foo = new File(remote,"foo");
		FileUtils.touch(foo);
		hg(remote,"add","foo");
		hg(remote,"commit","-m","Initial import");
		
		hg(remote,"branch","alice");
		FileUtils.writeStringToFile(foo, "Hello from alice");
		hg(remote,"commit","-m","Added hello message from alice");
		String aliceRevision = hg(remote,"tip","--template","{node}").toString();
		assertNotNull(aliceRevision);
		
		hg(remote,"update","-C","default");
		hg(remote,"branch","bob");
		FileUtils.writeStringToFile(foo, "Hello from bob");
		hg(remote,"commit","-m","Added hello message from bob");
		String bobRevision = hg(remote,"tip","--template","{node}").toString();
		
		FreeStyleProject alice = createJob(remote.getAbsolutePath(),"alice","alice","default");
		FreeStyleProject bob = createJob(remote.getAbsolutePath(),"bob","bob","default");

		//run alices integration then bobs
		Future<FreeStyleBuild> aliceBuild = alice.scheduleBuild2(0);
		Future<FreeStyleBuild> bobBuild = bob.scheduleBuild2(0);
		
		aliceBuild.get();
		FreeStyleBuild b = bobBuild.get();

		//the jobs should now be present in the integration branch merged in the same order as comitted.
		ByteArrayOutputStream revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		String[] revArr = revisions.toString().split("\n");
		//The first element is the initial commit
		assertTrue(revArr.length == 2);
		assertTrue(revArr[1].startsWith("Merge of revision " + aliceRevision));
		assertEquals(Result.FAILURE,b.getResult());
		
		//it should get rejected
		//then rebase with 
		
		//make sure we are on bob
		hg(remote,"update","bob");
		hg(remote,1,"merge","default");
		FileUtils.writeStringToFile(foo, "Hello from alice\nHello from bob");
		hg(remote,"resolve","--mark","foo");
		hg(remote,"commit","-m","Merged integration branch into bob");
		String bobRevision2 = hg(remote,"tip","--template","{node}").toString();
		
		Future<FreeStyleBuild> bobBuild2 = bob.scheduleBuild2(0);
		bobBuild2.get();
		
		revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		revArr = revisions.toString().split("\n");
		//The first element is the initial commit
		assertTrue(revArr[2].startsWith("Merge of revision " + bobRevision2));
		
	}
	
	/**
	 * Test that a merge conflict does not ruin the integration branch
	 */
	public void testRejectedCommitMerge() throws Exception {
		//TODO
	}

	/**
	 * Test that a rejected commit does not ruin the integration branch
	 */
	public void testRejectedCommitFailure() throws Exception {
		
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
		//For some reason bobs build fails
		TestBuilder failBuilder = new TestBuilder() {

			@Override
			public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
					BuildListener listener) throws InterruptedException,
					IOException {
				build.setResult(Result.FAILURE);
				return false;
			}
		};
		bob.getBuildersList().add(failBuilder);
		//trigger jenkins build
		Future<FreeStyleBuild> aliceBuild = alice.scheduleBuild2(0);
		Future<FreeStyleBuild> bobBuild = bob.scheduleBuild2(0);
		
		//We only need to wait for bobs build as alices is bound to complete first
		aliceBuild.get();
		bobBuild.get();
		
		//the jobs should now be present in the integration branch merged in the same order as comitted.
		ByteArrayOutputStream revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		String[] revArr = revisions.toString().split("\n");
		//The first element is the initial commit
		assertTrue(revArr[1].startsWith("Merge of revision " + aliceRevision));
		//Only alices commit is integrated
		assertTrue(revArr.length == 2);
		
		//alice works on
		hg(remote,"update","alice");
		hg(remote,"merge","default");
		hg(remote,"commit","-m","Synched with default");
		String aliceRevision2 = hg(remote,"tip","--template","{node}").toString();
		alice.scheduleBuild2(0).get();
		FileUtils.writeStringToFile(foo, "hello, world from foo");
		hg(remote,"commit","-m","Added sender in bar");
		String aliceRevision3 =  hg(remote,"tip","--template","{node}").toString();
		alice.scheduleBuild2(0).get();
		
		bob.getBuildersList().remove(failBuilder);
		hg(remote,"update","bob");
		FileUtils.writeStringToFile(bar, "hello, world from bar");
		hg(remote,"commit","-m","Added sender in bar");
		String bobRevision2 = hg(remote,"tip","--template","{node}").toString();
		bob.scheduleBuild2(0).get();
		
		//this time around the merge should succeed
		
		revisions = hg(remote,"log","--branch","default","--template","{desc}\n","-r",":");
		revArr = revisions.toString().split("\n");
		
		assertTrue(revArr[2].startsWith("Merge of revision " + aliceRevision2));
		assertTrue(revArr[3].startsWith("Merge of revision " + aliceRevision3));
		assertTrue(revArr[4].startsWith("Merge of revision " + bobRevision2));
		assertTrue(revArr.length == 5);
	}
	
	/**
	 * Test that a reset will test and integrate false negatives
	 */
	
	public void testReset() throws Exception {
		//do some work on a branch that succeeds
		//init branch
		//do 3 commits
		setup();
		File remote = createTempDirectory();
		hg(remote,"init");
		File foo = new File(remote,"foo");
		FileUtils.touch(foo);
		hg(remote,"add","foo");
		
		FreeStyleProject alice = createJob(remote.getAbsolutePath(),"alice","alice","default");
		
		//this one goes fine
		hg(remote, "branch","alice");
		FileUtils.writeStringToFile(foo,"Hello world");
		hg(remote, "commit","-m","Added hello message");
		
		alice.scheduleBuild2(0).get();
		
		//now fail all the resulting builds
		TestBuilder failBuilder = new TestBuilder() {

			@Override
			public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
					BuildListener listener) throws InterruptedException,
					IOException {
				// TODO Auto-generated method stub
				build.setResult(Result.FAILURE);
				return false;
			}	
		};
		
		FileUtils.writeStringToFile(foo, "Helo world");
		hg(remote, "commit","-m","Introduced typo on purpose");
		String badRevision = hg(remote,"tip","--template","{node}").toString();
		
		alice.scheduleBuild2(0);
		
		FileUtils.writeStringToFile(foo, "Hello, world!");
		hg(remote,"commit","-m","Added punctuation");
		
		alice.scheduleBuild2(0);
		
		alice.getBuildWrappersList().remove(PretestedIntegrationBuildWrapper.class);
		
		//now reset the internal pointer
		PretestedIntegrationBuildWrapper buildWrapper = new PretestedIntegrationBuildWrapper(new Mercurial(true,"default"));
		alice.getBuildWrappersList().add(buildWrapper);
		
		//TODO: Figure out how to wait for the two builds to terminate
	}
	
	/**
	 * Test that if a build has been integrated after succeeded, even though the commit is verifiable
	 * it will not be tested.
	 */
	public void testResetAfterIntegration() throws Exception {
		//TODO
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
