package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.EnvVars;
import hudson.model.EnvironmentContributor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class EnvVarsIT {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private static Repository repository;
    private static String REPOSITORY;
    private static final String READY_BRANCH = "ready/cheese";
    private static final String INTEGRATION_BRANCH = "integrationBranch";

    @Before
    public void setup() throws Exception {
        createRepository();
    }

    @After
    public void tearDown() throws Exception {
        repository.close();

        if (repository.getDirectory().exists()) {
            FileUtils.deleteDirectory(repository.getDirectory().getParentFile());
        }
    }

    @Test
    public void testEnvironmentVariables() throws Exception {
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins,
                TestUtilsFactory.STRATEGY_TYPE.SQUASH,
                "${REPOSITORY}",
                true,
                "${INTEGRATION_BRANCH}");

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        QueueTaskFuture<FreeStyleBuild> build = project.scheduleBuild2(0);
        jenkins.waitUntilNoActivityUpTo(60000);
        jenkins.assertBuildStatusSuccess(build);
        System.out.println("Built with branch '" + INTEGRATION_BRANCH + "' on repository '" + REPOSITORY + "'.");
        assert builder.getEnvVars().get("INTEGRATION_BRANCH", "default").equals(INTEGRATION_BRANCH);
        assert builder.getEnvVars().get("REPOSITORY", "default").equals(REPOSITORY);
    }

    @TestExtension("testEnvironmentVariables")
    public static class JobScopedInjection extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Job j, EnvVars envs, TaskListener listener) {
            envs.put("REPOSITORY", REPOSITORY);
            envs.put("INTEGRATION_BRANCH", INTEGRATION_BRANCH);
        }
    }

    private void createRepository() throws IOException, GitAPIException {
        File repo = new File("EnvVar - " + UUID.randomUUID().toString().substring(0, 6) + "/.git");
        if (repo.getAbsoluteFile().exists()) {
            FileUtils.deleteDirectory(repo.getParentFile());
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(repo.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        Git git = new Git(repository);

        File readme = new File(repository.getDirectory().getParent().concat("/" + "readme"));
        FileUtils.writeStringToFile(readme, "commit 1\n");
        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(INTEGRATION_BRANCH);
        createBranchCommand.call();

        createBranchCommand = git.branchCreate();
        createBranchCommand.setName(READY_BRANCH);
        createBranchCommand.call();

        CheckoutCommand checkout = git.checkout();
        checkout.setName(READY_BRANCH);
        checkout.call();

        FileUtils.writeStringToFile(readme, "commit 2\n");
        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        checkout = git.checkout();
        checkout.setName(INTEGRATION_BRANCH);
        checkout.call();

        DeleteBranchCommand deleteBranchCommand = git.branchDelete();
        deleteBranchCommand.setBranchNames("master");
        deleteBranchCommand.call();

        REPOSITORY = repository.getDirectory().getAbsolutePath();
    }
}
