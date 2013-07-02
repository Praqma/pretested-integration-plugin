package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;

import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class PretestedIntegrationActionTest extends HudsonTestCase {

	public void testShouldNotBeShowed() throws Exception {
		PretestedIntegrationAction action = mockedAction();
		assertNull(action.getIconFileName());
		assertNull(action.getDisplayName());
	}
	
	public void testShouldInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		DummySCM scmInterface = new DummySCM();
		scmInterface.setCommit(new Commit<String>("test"));
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		
		assertTrue(action.initialise());
	}
	
	public void testShouldNotInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		AbstractSCMInterface scmInterface = new DummySCM();
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		
		assertFalse(action.initialise());
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
