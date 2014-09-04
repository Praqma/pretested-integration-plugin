package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;

import junit.framework.TestCase;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jvnet.hudson.test.HudsonTestCase;

public class PretestedIntegrationBuildWrapperTest extends HudsonTestCase {

	public void testShouldEnsurePublisher() throws Exception {
		
		PretestedIntegrationBuildWrapper buildWrapper = new PretestedIntegrationBuildWrapper(new DummySCM(null));
		
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		FreeStyleBuild build = new FreeStyleBuild(project);
		
		buildWrapper.ensurePublisher(build);
		
		TestCase.assertNotNull(project.getPublishersList().get(PretestedIntegrationPostCheckout.class));
	}
}
