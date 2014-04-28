package org.jenkinsci.plugins.pretestedintegration;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.StreamBuildListener;

import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class AbstractSCMBridgeTest extends HudsonTestCase {

	public void testShouldIncludeDummySCMExtension() throws Exception {
		boolean includedInInterfaceDescriptors = false;
		boolean includedInDescriptors = false;
		List<SCMBridgeDescriptor<AbstractSCMBridge>> interfaceDescriptors = AbstractSCMBridge.all();
		for(SCMBridgeDescriptor<AbstractSCMBridge> i : interfaceDescriptors){
			if(i.getDisplayName().equals("DummySCM"))
				includedInInterfaceDescriptors = true;
		}
		assertTrue(includedInInterfaceDescriptors);
		
		List<SCMBridgeDescriptor<?>> descriptors = AbstractSCMBridge.getDescriptors();
		for(SCMBridgeDescriptor<?> d : descriptors){
			if(d.getDisplayName().equals("DummySCM"))
				includedInDescriptors = true;
		}
		assertTrue(includedInDescriptors);
	}
	
	public void testShouldGetCorrectDescriptor(){
		DummySCM scm = new DummySCM(null);
		assertEquals("DummySCM",scm.getDescriptor().getDisplayName());
	}
	
	public void testShouldBeCommited() throws Exception {
		DummySCM scm = new DummySCM(null);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getResult()).thenReturn(Result.SUCCESS);
		Launcher launcher = mock(Launcher.class);

		OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		
		assertFalse(scm.isCommited());
		scm.handlePostBuild(build, launcher, listener);
		assertTrue(scm.isCommited());
	}
	
	public void testShouldBeRolledBack() throws Exception {
		DummySCM scm = new DummySCM(null);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getResult()).thenReturn(Result.UNSTABLE);
		Launcher launcher = mock(Launcher.class);

		OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		
		assertFalse(scm.isRolledBack());
		scm.handlePostBuild(build, launcher, listener);
		assertTrue(scm.isRolledBack());
	}
	
}
