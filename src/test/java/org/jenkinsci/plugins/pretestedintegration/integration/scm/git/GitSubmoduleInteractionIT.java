package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by mads on 1/15/18.
 */
public class GitSubmoduleInteractionIT {

    @Rule
    public JenkinsRule jr = new JenkinsRule();

    List<Repository> repos;

    @Test
    public void testSubmoduleBehaviourSQUASH() throws Exception {
        repos = TestUtilsFactory.createRepoWithSubmodules("main","sub1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jr, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repos.get(1));
        GitSCM scm = (GitSCM)project.getScm();
        SubmoduleOption so = new SubmoduleOption(false,true,false, null,null, false);
        scm.getExtensions().add(so);
        project.save();
        FreeStyleBuild fb = jr.buildAndAssertSuccess(project);
        FilePath readmeInWorkspace = new FilePath(fb.getWorkspace(),"sub1/subreadme.md");
        assertTrue(readmeInWorkspace.readToString().contains("Change in subrepo"));
    }

    @Test
    public void testSubmoduleBehaviourACCUMULATED() throws Exception {
        repos = TestUtilsFactory.createRepoWithSubmodules("main","sub1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jr, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repos.get(1));
        GitSCM scm = (GitSCM)project.getScm();
        SubmoduleOption so = new SubmoduleOption(false,true,false, null,null, false);
        scm.getExtensions().add(so);
        project.save();
        FreeStyleBuild fb = jr.buildAndAssertSuccess(project);
        FilePath readmeInWorkspace = new FilePath(fb.getWorkspace(),"sub1/subreadme.md");
        assertTrue(readmeInWorkspace.readToString().contains("Change in subrepo"));
    }

    @After
    public void cleanup() throws Exception {
        for(Repository repo : repos) {
            TestUtilsFactory.destroyRepo(repo);
        }
    }
}
