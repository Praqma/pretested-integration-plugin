package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Hudson;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

public abstract class MercurialTestCase extends HudsonTestCase {

	StreamTaskListener listener;
	Launcher launcher;
	
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
	
	public static void cleanup(File path) throws IOException{
		FileUtils.deleteDirectory(path);
	}
}
