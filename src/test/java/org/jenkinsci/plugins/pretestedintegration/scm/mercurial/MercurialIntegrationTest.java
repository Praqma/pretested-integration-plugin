package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.util.StreamTaskListener;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.scm.mercurial.Mercurial;

import org.junit.*;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.plugins.mercurial.*;

import static org.mockito.Mockito.*;

//@RunWith(JUnit4.class)
public class MercurialIntegrationTest extends HudsonTestCase {

	StreamTaskListener listener;
	Launcher launcher;
	
	/* Integration test cases */
	
	//@Test
	public void testShouldPrepareWithDevBranch() throws Exception{
		setup();
		File dir = createTempDirectory();
		Mercurial plugin = new Mercurial("0","");
		plugin.setWorkingDirectory(new FilePath(dir));
		
		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//Setup the repository
		hg(dir, "init");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "log");
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		hg(dir, "log");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		
		FreeStyleBuild build = new FreeStyleBuild(project);
		
		String rev = hg(dir, "log","--template","{node}","-l","1").toString("UTF-8");	
		Commit<String> commit = new Commit<String>(rev);
		
		plugin.prepareWorkspace(build, launcher, blistener, commit);
		
		File bar = new File(dir,"bar");
		
		assertTrue(bar.exists());
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
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
		
		Mercurial plugin = new Mercurial("0","");
		plugin.setWorkingDirectory(new FilePath(dir));
		
		hg(dir, "init");
		shell(dir,"touch","foo");
		hg(dir, "add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		hg(dir, "update","default");
		hg(dir, "merge","test");
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);

		Future<FreeStyleBuild> b = project.scheduleBuild2(0);
		FreeStyleBuild build = spy(b.get());
		when(build.getResult()).thenReturn(Result.SUCCESS);

		BuildListener bListener = mock(BuildListener.class);
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		assertNotNull(build.getResult());
		
		plugin.handlePostBuild(build, launcher, bListener);
		
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().isEmpty());
		
		assertTrue(hg(dir,"log","-rtip","--template","{desc}").
				toString()
				.startsWith("Successfully integrated development branch"));
		
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
		
		Mercurial plugin = new Mercurial("0","");
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
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);

		Future<FreeStyleBuild> b = project.scheduleBuild2(0);
		FreeStyleBuild build = spy(b.get());
		when(build.getResult()).thenReturn(Result.UNSTABLE);
		assertNotNull(build);
		
		String head = hg(dir,"tip","--template","{node}").toString();
		
		BuildListener bListener = mock(BuildListener.class);
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().startsWith("M bar"));
		
		plugin.handlePostBuild(build, launcher, bListener);
		
		assertTrue(hg(dir,"branch").toString().startsWith("default"));
		assertTrue(hg(dir,"status").toString().isEmpty());
		assertTrue(hg(dir,"tip","--template","{node}").
				toString().equals(head));
	}

	public void testShouldNotHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		Mercurial plugin = new Mercurial("0","");
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
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		Commit<String> result = plugin.nextCommit(build, launcher, blistener, new Commit<String>(revision));
		
		assertNull(result);
	}
	
	public void testShouldHaveNextCommit() throws Exception {

		setup();
		File dir = createTempDirectory();
		Mercurial plugin = new Mercurial("0","");
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
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		assertNotNull(plugin.nextCommit(build, launcher, blistener, new Commit<String>("0")));
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
		Mercurial plugin = new Mercurial("0","");
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
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		//FreeStyleBuild build = new FreeStyleBuild(project);	
		Future<FreeStyleBuild> f = project.scheduleBuild2(0);
		FreeStyleBuild build = f.get();
		System.out.println("Revision: " + rev);
		assertNotNull(plugin.nextCommit(build, launcher, blistener, new Commit<String>(rev)));
		//assertTrue(plugin.hasNextCommit(build, launcher, blistener));
		//assertTrue(plugin.commitFromDate(build, launcher, blistener, date).getId().equals(rev));
	}

	public void testShouldCauseMergeConflict() throws Exception {
		setup();
		File dir = createTempDirectory();
		Mercurial plugin = new Mercurial("0","");
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
		
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true, false);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);
		
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
		boolean exceptionThrown = false;
		try{
			plugin.prepareWorkspace(build, launcher, blistener, new Commit<String>(revision));
		} catch (AbortException e){
			exceptionThrown = true;
		}
		
		assertTrue(exceptionThrown);
	}
	
	/* Helper functions for the test cases */

	//@Before
	public void setup() throws IOException {
		//listener = mock(StreamTaskListener.class); //new StreamTaskListener(System.out, Charset.defaultCharset());
        //launcher = mock(Launcher.class);
        listener = new StreamTaskListener(System.out,Charset.defaultCharset());
        launcher = Hudson.getInstance().createLauncher(listener);
    }
	
	//Thank you MercurialSCM
		static ProcStarter launch(Launcher launcher) {
			return launcher.launch().envs(Collections.singletonMap("HGPLAIN", "true"));
			
		}
		protected final void shell(String... cmds) throws Exception {
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
			Assert.assertEquals(0, launcher.launch().cmds(cmds).stdout(out).join());
		}
		protected final void shell(File dir, String... cmds) throws Exception {
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
			Assert.assertEquals(0, launcher.launch().cmds(cmds).pwd(dir).stdout(out).join());
		}
		
	    protected final void hg(String... args) throws Exception {
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
	        List<String> cmds = assembleHgCommand(args);
	        Assert.assertEquals(0, launch(launcher).cmds(cmds).stdout(out).join());
	    }

	    protected final ByteArrayOutputStream hg(File dir, String... args) throws Exception {
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
	        List<String> cmds = assembleHgCommand(args);
	        Assert.assertEquals(0, launch(launcher).cmds(cmds).pwd(dir).stdout(out).join());
	        return out;
	    }	
	    
		public List<String> assembleHgCommand(String[] args){
	        List<String> cmds = new ArrayList<String>();
	        cmds.add("hg");
	        cmds.add("--config");
	        cmds.add("ui.username=nobody@nowhere.net");
	        cmds.addAll(Arrays.asList(args));
	        return cmds;
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
	

}
