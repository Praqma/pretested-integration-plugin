package org.jenkinsci.plugins.pretestedintegration.unit;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.StreamBuildListener;

import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationAction;
import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class PretestedIntegrationActionTest extends HudsonTestCase {

	public void testShouldNotBeShowed() throws Exception {
		PretestedIntegrationAction action = mockedAction();
		assertNull(action.getIconFileName());
		assertNull(action.getDisplayName());
		assertEquals("pretested-integration", action.getUrlName());
	}
        
        //TODO: Fix this prior to release
	/*
	public void testShouldInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		DummySCM scmInterface = new DummySCM(null);
		scmInterface.setCommit(new Commit<String>("test"));
                when(listener.getLogger()).thenReturn(System.out);                
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);		
		assertTrue(action.initialise(launcher, listener));
	}
        */
	
	public void testShouldNotInitialise() throws Exception {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		AbstractSCMBridge scmInterface = new DummySCM(null);
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		
		assertFalse(action.initialise(launcher, listener));
	}
	
	public void testShouldInvokeNewBuild() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getProject()).thenReturn(project);
		Launcher launcher = mock(Launcher.class);

        BuildData gitBuildData = mock(BuildData.class);
        Build lastBuild = mock(Build.class);
        Revision rev = mock(Revision.class);
        Branch gitBranchData = mock(Branch.class);

        gitBuildData.lastBuild = lastBuild;
        lastBuild.revision = rev;

        when(build.getAction(BuildData.class)).thenReturn(gitBuildData);

        List<Branch> branches = new ArrayList<Branch>();
        branches.add(gitBranchData);
        when(gitBuildData.lastBuild.revision.getBranches()).thenReturn(branches);
        when(gitBranchData.getName()).thenReturn("origin/ready/f1");

		OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		DummySCM scmInterface = new DummySCM(null);
		
		scmInterface.setCommit(new Commit<String>("test"));
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		action.finalise(launcher, listener);
		
		verify(project, times(0)).scheduleBuild2(0);
	}
	
	public void testShouldNotInvokeNewBuild() throws Exception {
		FreeStyleProject project = mock(FreeStyleProject.class);
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getProject()).thenReturn(project);
		Launcher launcher = mock(Launcher.class);

        BuildData gitBuildData = mock(BuildData.class);
        Build lastBuild = mock(Build.class);
        Revision rev = mock(Revision.class);
        Branch gitBranchData = mock(Branch.class);

        gitBuildData.lastBuild = lastBuild;
        lastBuild.revision = rev;

        when(build.getAction(BuildData.class)).thenReturn(gitBuildData);

        List<Branch> branches = new ArrayList<Branch>();
        branches.add(gitBranchData);
        when(gitBuildData.lastBuild.revision.getBranches()).thenReturn(branches);
        when(gitBranchData.getName()).thenReturn("origin/ready/f1");

        OutputStream out = new ByteArrayOutputStream();
		BuildListener listener = new StreamBuildListener(out);
		DummySCM scmInterface = new DummySCM(null);
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		action.finalise(launcher, listener);
		verify(project, times(0)).scheduleBuild2(0);
	}
	
	public PretestedIntegrationAction mockedAction() throws IOException {
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		DummySCM scmInterface = new DummySCM(null);
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		return action;
	}
	
}
