package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

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

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.junit.*;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.plugins.mercurial.*;

import static org.mockito.Mockito.*;

//@RunWith(JUnit4.class)
public class MercurialIntegrationTest extends HudsonTestCase {

	StreamTaskListener listener;
	Launcher launcher;
	
	//@Before
	public void setup() throws IOException {
		//listener = mock(StreamTaskListener.class); //new StreamTaskListener(System.out, Charset.defaultCharset());
        //launcher = mock(Launcher.class);
        listener = new StreamTaskListener(System.out,Charset.defaultCharset());
        launcher = Hudson.getInstance().createLauncher(listener);
    }
	
	//@Test
	public void testShouldPrepareWithDevBranch() throws Exception{
		setup();
		File dir = createTempDirectory();
		PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();

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
		
		FreeStyleProject project = createFreeStyleProject();
		
		//Need to get the correct build
		
		String rev = hg(dir, "log","--template","'{node}'","-l","1").toString("UTF-8");	
		PretestedIntegrationSCMCommit commit = new PretestedIntegrationSCMCommit(rev);
		
		//plugin.prepareWorkspace(build, launcher, blistener, commit);
		
		File bar = new File(dir,"bar");
		//assertTrue(bar.exists());
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
