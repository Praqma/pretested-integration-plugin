package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;
import junit.framework.TestCase;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jvnet.hudson.test.HudsonTestCase;

public class PretestedIntegrationBuildWrapperTest extends HudsonTestCase {

    public void testShouldEnsurePublisher() throws Exception {

        PretestedIntegrationBuildWrapper buildWrapper = new PretestedIntegrationBuildWrapper(new DummySCM(null));

        FreeStyleProject project = Jenkins.getInstance().createProject(FreeStyleProject.class, "testproject");
        FreeStyleBuild build = new FreeStyleBuild(project);

        buildWrapper.ensurePublisher(build);

        TestCase.assertNotNull(project.getPublishersList().get(PretestedIntegrationPostCheckout.class));
    }
}
