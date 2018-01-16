package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * This test will verify the pretested integration process work with a configuration using git submodules.
 * It is interesting to verify as git submodules are processes also as a git SCM extension, we want to ensure
 * it the contributing change to be integrated contain updates to submodules those changes are also part of
 * the final integration commit delivered to the integration.
 * Further we want to ensure that the workspace established that the job run for example build step on are correct
 * and have the expected version of the submodule.
 *
 *
 */
public class GitSubmoduleInteractionIT {

    @Rule
    public JenkinsRule jr = new JenkinsRule();

    Map<String, Repository> repos;


    /**
     * This is just a helper method to create a git test repository with a submodule we can use in our test Jenkins job.
     *
     * The method will create two repositories.
     * One repository we call main repository and one we call sub repository.
     * The sub repository will have one initial commit.
     * Then the sub repository will be added in the initial commit together with a change to the main repository.
     *
     * The commits we want to use for testing is then established by:
     * 1. Adding a new commit in the sub repository
     * 2. In the main repository creating a ready branch, adding a change in a local file, as well as updating
     * to latest version of the submodule and binding that to the main.
     *
     * The will result in a commit on a ready branch on the main repo, where there is a change in both main repository
     * content as well as a change in the submodule.
     *
     * It is not in test utils, as this is the only test using it.
     */
    public static Map<String, Repository> createRepoWithSubmodules(String mainRepoName, String subRepoName) throws Exception {
        Map<String, Repository> createdRepositories = new HashMap<>();

        // Create sub repository
        File subRepoGitFile = new File(subRepoName+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForSubRepo = new File(subRepoName);
        Repository subRepo = new FileRepository(subRepoGitFile);
        subRepo.create(true);
        // Adding to our list as we need to clean up after test
        createdRepositories.put("subRepo", subRepo);

        // Add initial commit to the sub repo using a clone of the repo
        Git.cloneRepository().setURI("file:///" + subRepoGitFile.getAbsolutePath()).setDirectory(workDirForSubRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        // Commits:
        Git gitSub = Git.open(workDirForSubRepo);
        File subRepoReadme = new File(workDirForSubRepo, "subreadme.md");
        FileUtils.writeStringToFile(subRepoReadme, "Initial commit from subrepo\n", true);
        gitSub.add().addFilepattern(subRepoReadme.getName()).call();
        gitSub.commit().setMessage("Initial commit for subrepo").call();
        gitSub.push().setPushAll().call();


        // Create the main repository
        File repo = new File(mainRepoName + ".git");
        File workDirForRepo = new File(mainRepoName);
        Repository mainRepo = new FileRepository(repo);
        mainRepo.create(true);
        // Adding to our list as we need to clean up after test
        createdRepositories.put("mainRepo", mainRepo);

        // Prepare main repo adding commit and the sub repo as a git submodule
        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git gitMain = Git.open(workDirForRepo);
        File mainReadme = new File(workDirForRepo, "readme.md");
        FileUtils.writeStringToFile(mainReadme, "Initial commit from main repository, including submodule\n", true);
        gitMain.add().addFilepattern(mainReadme.getName()).call();
        gitMain.submoduleAdd().setPath(subRepoName).setURI("file:///"+ subRepoGitFile.getAbsolutePath()).call();
        gitMain.commit().setMessage("Initial commit for main repo, including submodule").call();
        gitMain.push().setPushAll().call();


        // Add the change in sub repo, we need to use below to update the sub repo as submodule in main
        // We do it by doing it in the cloned sub repo, outside the main repository and push to the remote and the new
        // version inside the main later.
        // We could also have done it directly in the main repository, but code writing was easier this way and result
        // the same.
        FileUtils.writeStringToFile(subRepoReadme, "Change in subrepo\n", true);
        gitSub.add().addFilepattern(subRepoReadme.getName()).call();
        gitSub.commit().setMessage("Change in subrepo").call();
        gitSub.push().setPushAll().call();


        // Now we will need to use the new version of the sub repo above in our main repository.
        // To this we need to do a git pull in the submodule in main, thus we need a handle to the git repo
        // in our test setup
        File gitSubInMainDirectory = new File(workDirForRepo, ".git/modules/sub1");
        Git gitSubInMain = Git.open(gitSubInMainDirectory);
        // Pull changes
        gitSubInMain.pull().call();

        // At this point we have a new version of our sub repo, submodule, in the main and we will add that new version
        // to main together with a change in main.
        // Change in main:
        FileUtils.writeStringToFile(mainReadme, "Change to readme from main\n", true);
        gitMain.add().addFilepattern(mainReadme.getName()).call();

        // Adding the submodule new version:
        gitMain.add().addFilepattern("sub1").call();

        // Creating a ready branch, as our Jenkins job will run on ready-branches in our default plugin configuration.
        gitMain.branchCreate().setName("ready/updateToSubrepo").call();
        gitMain.checkout().setName("ready/updateToSubrepo").call();
        gitMain.commit().setMessage("Updated readme and added new version of submodule").call();
        gitMain.push().setPushAll().call();

        // clean-up local clone of test repo used for working with them.
        FileUtils.deleteDirectory(workDirForRepo);
        FileUtils.deleteDirectory(workDirForSubRepo);

        return createdRepositories;
    }


    /**
     * Will test the plugin with happy day scenario using the squash strategy
     * and submodule behavior.
     *
     * We trying to integrate a change in a main repository, where the change introduces both change in the main repo
     * but also a new version of the git submodule.
     *
     * We want to verify two things:
     *
     * 1. The workspace in the job is established correct during the build phase where one typically verify the change.
     *    In this test this means that the submodule must be updated and contain the change that came with the ready-
     *    branch.
     * 2. The commit pushed to the integration branch contain also the same change where submodule is part of it.
     *
     * @throws Exception
     */
    @Test
    public void testSubmoduleBehaviourSQUASH() throws Exception {
        repos = createRepoWithSubmodules("main","sub1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jr, TestUtilsFactory.STRATEGY_TYPE.SQUASH, repos.get("mainRepo"));
        GitSCM scm = (GitSCM)project.getScm();
        SubmoduleOption so = new SubmoduleOption(false,true,false, null,null, false);
        scm.getExtensions().add(so);
        project.save();
        FreeStyleBuild fb = jr.buildAndAssertSuccess(project);
        FilePath readmeInWorkspace = new FilePath(fb.getWorkspace(),"sub1/subreadme.md");
        assertTrue(readmeInWorkspace.readToString().contains("Change in subrepo"));
    }

    /**
     * See above description for testSubmoduleBehaviourSQUASH()
     * @throws Exception
     */
    @Test
    public void testSubmoduleBehaviourACCUMULATED() throws Exception {
        repos = createRepoWithSubmodules("main","sub1");
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jr, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repos.get("mainRepo"));
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
        for(Repository repo : repos.values()) {
            TestUtilsFactory.destroyRepo(repo);
        }
    }
}
