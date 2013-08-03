package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.io.OutputStream;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.StreamBuildListener;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class PretestedIntegrationActionTest extends HudsonTestCase {

	public void testShouldNotBeShowed() throws Exception {
		PretestedIntegrationAction action = mockedAction();
		assertNull(action.getIconFileName());
		assertNull(action.getDisplayName());
		assertEquals("pretested-integration", action.getUrlName());
	}
	
	public void testShouldInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		DummySCM scmInterface = new DummySCM();
		scmInterface.setCommit(new Commit<String>("test"));
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		
		assertTrue(action.initialise(launcher, listener));
	}
	
	public void testShouldNotInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		AbstractSCMInterface scmInterface = new DummySCM();
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		
		assertFalse(action.initialise(launcher, listener));
	}
	
	public void testShouldInvokeNewBuild() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getProject()).thenReturn(project);
		Launcher launcher = mock(Launcher.class);

		OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		DummySCM scmInterface = new DummySCM();
		
		scmInterface.setCommit(new Commit<String>("test"));
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		action.finalise(launcher, listener);
		
		verify(project, times(1)).scheduleBuild2(0);
	}
	
	public void testShouldNotInvokeNewBuild() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getProject()).thenReturn(project);
		Launcher launcher = mock(Launcher.class);

		OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		DummySCM scmInterface = new DummySCM();
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		action.finalise(launcher, listener);
		verify(project, times(0)).scheduleBuild2(0);
	}
	
	public PretestedIntegrationAction mockedAction() throws IOException {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		DummySCM scmInterface = new DummySCM();
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		return action;
	}
	
}
