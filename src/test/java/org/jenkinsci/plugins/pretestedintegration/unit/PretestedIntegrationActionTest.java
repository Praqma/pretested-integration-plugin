package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationAction;
import org.junit.Test;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static org.mockito.Mockito.*;

public class PretestedIntegrationActionTest {

    @Test
    public void testShouldNotBeShowed() throws Exception {
        PretestedIntegrationAction action = mockedAction();
        assertNull(action.getIconFileName());
        assertNull(action.getDisplayName());
        assertEquals("pretested-integration", action.getUrlName());
    }

    @Test
    public void testShouldNotInitialise() throws Exception {
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener listener = mock(BuildListener.class);
        AbstractSCMBridge scmInterface = new DummySCM(new DummyIntegrationStrategy());
        PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
        assertFalse(action.initialise(launcher, listener));
    }

    @Test
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

        List<Branch> branches = new ArrayList<>();
        branches.add(gitBranchData);
        when(gitBuildData.lastBuild.revision.getBranches()).thenReturn(branches);
        when(gitBranchData.getName()).thenReturn("origin/ready/f1");

        OutputStream out = new ByteArrayOutputStream();
        BuildListener listener = new StreamBuildListener(out);
        DummySCM scmInterface = new DummySCM(null);

        scmInterface.setCommit(new Commit<>("test"));

        when(build.getResult()).thenReturn(Result.SUCCESS);

        PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
        action.finalise(launcher, listener);

        verify(project, times(0)).scheduleBuild2(0);
    }

    @Test
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

        List<Branch> branches = new ArrayList<>();
        branches.add(gitBranchData);
        when(gitBuildData.lastBuild.revision.getBranches()).thenReturn(branches);
        when(gitBranchData.getName()).thenReturn("origin/ready/f1");

        OutputStream out = new ByteArrayOutputStream();
        BuildListener listener = new StreamBuildListener(out);
        DummySCM scmInterface = new DummySCM(null);
        PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
        when(build.getResult()).thenReturn(Result.SUCCESS);

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
