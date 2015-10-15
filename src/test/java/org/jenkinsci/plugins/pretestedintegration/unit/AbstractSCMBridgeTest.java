package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.*;

public class AbstractSCMBridgeTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void testShouldIncludeDummySCMExtension() throws Exception {
        boolean includedInInterfaceDescriptors = false;
        boolean includedInDescriptors = false;
        List<SCMBridgeDescriptor<AbstractSCMBridge>> interfaceDescriptors = AbstractSCMBridge.all();
        for (SCMBridgeDescriptor<AbstractSCMBridge> i : interfaceDescriptors) {
            if (i.getDisplayName().equals("DummySCM")) {
                includedInInterfaceDescriptors = true;
            }
        }
        assertTrue(includedInInterfaceDescriptors);

        List<SCMBridgeDescriptor<?>> descriptors = AbstractSCMBridge.getDescriptors();
        for (SCMBridgeDescriptor<?> d : descriptors) {
            if (d.getDisplayName().equals("DummySCM")) {
                includedInDescriptors = true;
            }
        }
        assertTrue(includedInDescriptors);
    }

    @Test
    public void testShouldGetCorrectDescriptor() {
        DummySCM scm = new DummySCM(null);
        TestCase.assertEquals("DummySCM", scm.getDescriptor().getDisplayName());
    }

    @Test
    public void testShouldBeCommited() throws Exception {
        DummySCM scm = new DummySCM(null);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
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

        assertFalse(scm.isCommited());
        scm.handlePostBuild(build, launcher, listener);
        assertTrue(scm.isCommited());
    }

}
