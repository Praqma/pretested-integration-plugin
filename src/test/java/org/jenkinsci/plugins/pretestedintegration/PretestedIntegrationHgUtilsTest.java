package org.jenkinsci.plugins.pretestedintegration;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.util.StreamTaskListener;

import java.lang.reflect.InvocationTargetException;

import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;
import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.MercurialIntegrationTest;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.PretestedIntegrationSCMMercurial;

import org.junit.*;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.plugins.mercurial.*;

import static org.mockito.Mockito.*;

public class PretestedIntegrationHgUtilsTest extends MercurialIntegrationTest {

	//public void testShouldCreateInstance() throws Exception {
	//	genericTestConstructor(HgUtils.class);
	//}

	public void testShouldHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		shell(dir,"echo", "text",">>","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "branch","test");
		shell(dir,"echo", "more text",">>","foo");
		hg(dir, "commit","-m","\"changed foo\"");
		
		FreeStyleProject project = createFreeStyleProject();
		
		//assertTrue(plugin.hasNextCommit(build, launcher, blistener));
	}

	public void testShouldNotHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		hg(dir, "branch","test");
		shell(dir,"echo", "text",">>","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		
		FreeStyleProject project = createFreeStyleProject();
		
		//assertFalse(plugin.hasNextCommit(build, launcher, blistener));
	}

	public void testLogToCommitDictShouldReturnCommitInfo() throws Exception {

		setup();
		File dir = createTempDirectory();
		PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		shell(dir,"echo", "text",">>","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "branch","test");
		shell(dir,"echo", "more text",">>","foo");
		hg(dir, "commit","-m","\"changed foo\"");
		
		FreeStyleProject project = createFreeStyleProject();
		
		//String revision = "1";
		//BufferedReader logStdout = runScmCommand(
		//			build, launcher, listener, new String[]{"log", "-r", revision+":tip"});

		//assertNotNull(plugin.logToCommitDict(logStdout));
	}

	public void testLogToCommitDictShouldReturnNull() throws Exception {

		setup();
		File dir = createTempDirectory();
		PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		hg(dir, "branch","test");
		shell(dir,"echo", "text",">>","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		
		FreeStyleProject project = createFreeStyleProject();

		//String revision = "1";
		//BufferedReader logStdout = runScmCommand(
		//			build, launcher, listener, new String[]{"log", "-r", revision+":tip"});


		//assertNull(plugin.logToCommitDict(logStdout));
	}
}
