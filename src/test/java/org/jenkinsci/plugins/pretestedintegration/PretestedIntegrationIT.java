package org.jenkinsci.plugins.pretestedintegration;

import static org.mockito.Mockito.mock;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialSCM;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.jenkinsci.plugins.pretestedintegration.scm.mercurial.Mercurial;

public class PretestedIntegrationIT extends
		PretestedIntegrationTestCase {
	/**
	 * Given there are more than one commit waiting in line
	 * When the first build completes
	 * Then a second is scheduled
	 * @throws IOException
	 */
	public void testShouldScheduleMultipleBuilds() throws Exception {
		setup();
		File dir = createTempDirectory();
		//PretestedIntegrationSCMMercurial plugin = new PretestedIntegrationSCMMercurial();
		Mercurial plugin = new Mercurial(false,"");
		plugin.setWorkingDirectory(new FilePath(dir));

		System.out.println("Creating test repository at repository: " + dir.getAbsolutePath());
		
		//find out a good way to do this...
		/*hg(dir,"init");
		shell(dir,"touch","foo");
		hg(dir,"add","foo");
		hg(dir, "commit","-m","\"added foo\"");
		hg(dir, "branch","test");
		shell(dir,"touch","bar");
		hg(dir, "add","bar");
		hg(dir, "commit","-m","\"added bar\"");
		shell(dir,"touch","baz");
		hg(dir, "add","baz");
		hg(dir, "commit","-m","\"added baz\"");
		*/
		MercurialSCM scm = new MercurialSCM(null,dir.getAbsolutePath(),null,null,null,null, true);
		FreeStyleProject project = Hudson.getInstance().createProject(FreeStyleProject.class, "testproject");
		project.setScm(scm);

		project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(plugin));
		project.getPublishersList().add(new PretestedIntegrationPostCheckout());
		//Setup build and listener
		BuildListener blistener = mock(BuildListener.class);
		FreeStyleBuild build = new FreeStyleBuild(project);	
		
	}
}
