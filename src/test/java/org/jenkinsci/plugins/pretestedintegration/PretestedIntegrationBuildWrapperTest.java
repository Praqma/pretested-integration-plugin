package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;

import org.jvnet.hudson.test.HudsonTestCase;

public class PretestedIntegrationBuildWrapperTest extends HudsonTestCase {

	public void testShouldEnsurePublisher() throws Exception {
		
		PretestedIntegrationBuildWrapper buildWrapper = new PretestedIntegrationBuildWrapper(new DummySCM());
		
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		FreeStyleBuild build = new FreeStyleBuild(project);
		
		buildWrapper.ensurePublisher(build);
		
		assertNotNull(project.getPublishersList().get(PretestedIntegrationPostCheckout.class));
	}
}
