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
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * This test will verify the pretested integration process work with a configuration using git submodules.
 * It is interesting to verify as git submodules are processes also as a git SCM extension, we want to ensure
 * it the contributing change to be integrated contain updates to submodules those changes are also part of
 * the final integration commit delivered to the integration.
 * Further we want to ensure that the workspace established that the job run for example build step on are correct
 * and have the expected version of the submodule.
 */
public class GitSubmoduleInteractionIT {

    @Rule
    public JenkinsRule jr = new JenkinsRule();

    List<Repository> repos;


    /**
     * This is just a helper method to create a git test repository with a submodule we can use in our test Jenkins job
     *
     * It is not in test utils, as this is the only test using it.
     */
    public static List<Repository> createRepoWithSubmodules(String repoFolderName, String subRepoName) throws Exception {
        List<Repository> createdRepositories = new ArrayList<>();
        //Create root repository
        File repo = new File(repoFolderName + ".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);

        Repository repository = new FileRepository(repo);
        repository.create(true);

        File subRepoGitFile = new File(subRepoName+".git");
        File workDirForSubRepo = new File(subRepoName);

        Repository subRepo = new FileRepository(subRepoGitFile);
        subRepo.create(true);
        createdRepositories.add(subRepo);
        createdRepositories.add(repository);

        Git.cloneRepository().setURI("file:///" + subRepoGitFile.getAbsolutePath()).setDirectory(workDirForSubRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        //Prepare subrepo
        Git git = Git.open(workDirForSubRepo);
        File subRepoReadme = new File(workDirForSubRepo, "subreadme.md");
        FileUtils.writeStringToFile(subRepoReadme, "Initial commit from subrepo\n", true);
        git.add().addFilepattern(subRepoReadme.getName()).call();
        git.commit().setMessage("Initial commit for subrepo1").call();
        git.push().setPushAll().call();

        //Prepare main repo
        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git gitMain = Git.open(workDirForRepo);
        File mainReadme = new File(workDirForRepo, "readme.md");
        FileUtils.writeStringToFile(mainReadme, "Initial commit from main repository\n", true);
        gitMain.add().addFilepattern(mainReadme.getName()).call();
        gitMain.submoduleAdd().setPath(subRepoName).setURI("file:///"+ subRepoGitFile.getAbsolutePath()).call();
        gitMain.commit().setMessage("Initial commit for main repo").call();
        gitMain.push().setPushAll().call();

        //Change in subrepo
        FileUtils.writeStringToFile(subRepoReadme, "Change in subrepo\n", true);
        git.add().addFilepattern(subRepoReadme.getName()).call();
        git.commit().setMessage("Change in subrepo").call();
        git.push().setPushAll().call();

        File gitSubInMainDirectory = new File(workDirForRepo, ".git/modules/sub1");
        Git gitSubInMain = Git.open(gitSubInMainDirectory);
        gitSubInMain.pull().call();

        FileUtils.writeStringToFile(mainReadme, "Change to readme from main\n", true);
        gitMain.add().addFilepattern(mainReadme.getName()).call();

        //Use new version of submodule
        gitMain.branchCreate().setName("ready/updateToSubrepo").call();
        gitMain.checkout().setName("ready/updateToSubrepo").call();

        gitMain.add().addFilepattern("sub1").call();
        gitMain.commit().setMessage("Use new version of submodule").call();
        gitMain.push().setPushAll().call();

        FileUtils.deleteDirectory(workDirForRepo);
        FileUtils.deleteDirectory(workDirForSubRepo);

        return createdRepositories;
    }

    @Test
    public void testSubmoduleBehaviourSQUASH() throws Exception {
        repos = createRepoWithSubmodules("main","sub1");
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
        repos = createRepoWithSubmodules("main","sub1");
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
