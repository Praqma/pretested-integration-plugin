package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class MercurialTest extends MercurialTestCase {

	public void testShouldBeExtension(){
		boolean inDescriptorList = false;
		for(SCMInterfaceDescriptor<AbstractSCMInterface> d : AbstractSCMInterface.all()) {
			if(d.getDisplayName().equals("Mercurial"))
				inDescriptorList = true;
		}
		assertTrue(inDescriptorList);
	}
	
	public void testShouldInitialise() throws Exception {
		MercurialBridge mercurial = new MercurialBridge(true,"test");
		assertTrue(mercurial.getReset());
		assertEquals("test",mercurial.getBranch());
		
		mercurial = new MercurialBridge(false,"test");
		assertFalse(mercurial.getReset());
		assertEquals("test",mercurial.getBranch());
		
		mercurial = new MercurialBridge(false,"");
		assertFalse(mercurial.getReset());
		assertEquals("default",mercurial.getBranch());
		
		mercurial = new MercurialBridge(false,null);
		assertFalse(mercurial.getReset());
		assertEquals("default",mercurial.getBranch());
	}
	
	public void testShouldPrepareWithDevBranch() throws Exception{
		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));
		
		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());

		File bar = new File(dir,"bar");
		//Setup the repository
		hg(dir, "init");
		FileUtils.writeStringToFile(new File(dir,"foo"), "");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		//hg(dir, "log");
		hg(dir, "branch","test");
		FileUtils.writeStringToFile(bar, "");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		//hg(dir, "log");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		
		FreeStyleBuild build = new FreeStyleBuild(project);
		
		String rev = hg(dir, "log","--template","{node}","-l","1").toString("UTF-8");	
		Commit<String> commit = new Commit<String>(rev);
		
		plugin.prepareWorkspace(build, launcher, blistener, commit);
		
		
		assertTrue(bar.exists());
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		cleanup(dir);
	}
	
	/**
	 * Given that there are uncomitted changes in the integration branch
	 * And that the build is marked successful
	 * When handlePostBuild has been invoked
	 * Then there should be no more changes on the integration branch
	 * And the integration branch has a new commit with the changes
	 * @throws Exception
	 */
	public void testShouldCommitChanges() throws Exception {
		setup();
		File dir = createTempDirectory();
		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		MercurialBridge plugin = new MercurialBridge(false,"default");
		plugin.setWorkingDirectory(new FilePath(dir));
		
		hg(dir, "init");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","added foo");
		hg(dir, "branch","test");
		String revision = hg(dir,"tip","--template","{node}").toString();
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","added bar");
		hg(dir, "update","default");
		hg(dir, "merge","test");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),"test",null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);

		Future<FreeStyleBuild> b = project.scheduleBuild2(0);
		FreeStyleBuild build = spy(b.get());
		when(build.getResult()).thenReturn(Result.SUCCESS);


		OutputStream out = new ByteArrayOutputStream();
		BuildListener bListener = new StreamBuildListener(out);
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		assertNotNull(build.getResult());
		
		plugin.nextCommit(build, launcher, bListener, null);
		
		plugin.handlePostBuild(build, launcher, bListener);
		
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().isEmpty());
		
		assertTrue(hg(dir,"log","-rtip","--template","{desc}").
				toString()
				.startsWith("Merge of revision"));

		cleanup(dir);
	}
	
	/**
	 * Given that there are uncomitted changes in the integration branch
	 * And that the build is marked unsuccessful
	 * When handlePostBuild has been invoked
	 * Then the changes should no longer exist in the working tree
	 * And the history of the integation branch should be unchanged
	 * @param launcher
	 * @return
	 */
	public void testShouldRevertChanges() throws Exception {
		setup();
		File dir = createTempDirectory();
		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));
		
		hg(dir, "init");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","added foo");
		
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","added bar");
		hg(dir, "update","default");
		hg(dir, "merge","test");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);

		Future<FreeStyleBuild> b = project.scheduleBuild2(0);
		FreeStyleBuild build = spy(b.get());
		when(build.getResult()).thenReturn(Result.UNSTABLE);
		assertNotNull(build);
		
		String head = hg(dir,"tip","--template","{node}").toString();
		

		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		
		plugin.handlePostBuild(build, launcher, blistener);
		
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().isEmpty());
		assertTrue(hg(dir,"tip","--template","{node}").
				toString().equals(head));
		cleanup(dir);
	}

	public void testShouldNotHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		hg(dir, "branch","devtest");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		String revision = hg(dir,"log","-l1","--template","{node}").toString();
		//shell(dir, "echo",revision,">",".hg/currentBuildFile");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		Commit<String> result = plugin.nextCommit(build, launcher, blistener, new Commit<String>(revision));
		
		assertNull(result);
		cleanup(dir);
	}
	
	public void testShouldHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		shell(dir,"touch","bar3");
		hg(dir, "add","bar3");
		hg(dir, "commit","-m","\"added bar3\"");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		assertNotNull(plugin.nextCommit(build, launcher, blistener, new Commit<String>("0")));
		cleanup(dir);
	}

	/**
	 * Given that there are commits after a specified time
	 * When commitFromDate is invoked
	 * Then the next commit is returned
	 * @throws Exception
	 */
	public void testShouldGiveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		Date date = new Date();
		hg(dir,"init");
		shell(dir,"touch","foo");
		hg(dir,"add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		String rev = hg(dir,"tip","--template","{node}").toString();
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),"test",null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		//FreeStyleBuild build = new FreeStyleBuild(project);	
		Future<FreeStyleBuild> f = project.scheduleBuild2(0);
		FreeStyleBuild build = f.get();
		System.out.println("Revision: " + rev);
		assertNotNull(plugin.nextCommit(build, launcher, blistener, new Commit<String>(rev)));
		//assertTrue(plugin.hasNextCommit(build, launcher, blistener));
		//assertTrue(plugin.commitFromDate(build, launcher, blistener, date).getId().equals(rev));
		cleanup(dir);
	}

	public void testShouldCauseMergeConflict() throws Exception {
		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		hg(dir,"init");
		File hello = new File(dir,"hello");
		
		PrintWriter writer = new PrintWriter(hello, "UTF-8");
		writer.println("hello");
		writer.close();
		writer = null;
		
		hg(dir,"add","hello");
		hg(dir,"commit","-m","Added hello");
		hg(dir,"branch","test");
		
		writer = new PrintWriter(hello,"UTF-8");
		writer.println("hello, world");
		writer.close();
		writer = null;
		
		hg(dir,"commit","-m","Changed contents of hello in test branch");
		String revision = hg(dir,"tip","--template","{node}").toString();
		hg(dir,"update","-C","default");
		
		writer = new PrintWriter(hello,"UTF-8");
		writer.println("hello, jenkins");
		writer.close();
		writer = null;
		
		hg(dir,"commit","-m","Changed contents of hello in default branch");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		OutputStream out = new ByteArrayOutputStream();
		BuildListener blistener = new StreamBuildListener(out);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		boolean exceptionThrown = false;
		try{
			plugin.prepareWorkspace(build, launcher, blistener, new Commit<String>(revision));
		} catch (AbortException e){
			exceptionThrown = true;
		}
		
		assertTrue(exceptionThrown);
	}
	
	/**
	 * When a build is marked as NOT_BUILD
	 * And more builds are started
	 * And there are no changes
	 * Then all following builds should also be marked NOT_BUILD
	 */
	public void testShouldMarkBuildsAsNotBuilt() throws Exception {
		setup();
		File dir = createTempDirectory();
		MercurialBridge plugin = new MercurialBridge(false,"default");
		plugin.setWorkingDirectory(new FilePath(dir));

		hg(dir,"init");
		shell(dir,"touch","foo");
		hg(dir,"add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		//String rev = hg(dir,"tip","--template","{node}").toString();
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),"test",null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(plugin));

		Future<FreeStyleBuild> f = project.scheduleBuild2(0);
		FreeStyleBuild build = f.get();
		assertEquals(Result.SUCCESS,build.getResult());
		f = project.scheduleBuild2(0);
		build = f.get();
		assertEquals(Result.NOT_BUILT, build.getResult());
		f = project.scheduleBuild2(0);
		build = f.get();
		assertEquals(Result.NOT_BUILT, build.getResult());
		cleanup(dir);
	}
}
