package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.scm.SCM;
import hudson.slaves.DumbSlave;
import hudson.triggers.SCMTrigger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

public class TestUtilsFactory {

    public enum STRATEGY_TYPE {
        SQUASH, ACCUMULATED
    }

    public static final String AUTHOR_NAME = "john Doe";
    public static final String AUTHOR_EMAIL = "Joh@praqma.net";

    public static int countCommits(Repository repository) {
        Git git = new Git(repository);
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for (; iterator.hasNext(); ++commitCount) {
                iterator.next();
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return commitCount;
    }

    public static int countCommits(File workDir) throws IOException {
        Git git = Git.open(workDir);
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for (; iterator.hasNext(); ++commitCount) {
                iterator.next();
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        } finally {
            git.close();
        }

        return commitCount;
    }

    public static int countCommits(Git git) throws IOException {
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for (; iterator.hasNext(); ++commitCount) {
                iterator.next();
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
            git.close();
        }

        return commitCount;
    }

    // Count commits on the branch with name branch.
    // Jgit example code modified from here:
    // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/api/WalkRev.java
    public static int countCommitsOnBranch(Git git, String branch) throws IOException {

        Ref head = git.getRepository().getRef(branch);
        RevWalk walk = new RevWalk(git.getRepository());
        RevCommit commit = walk.parseCommit(head.getObjectId());
        walk.markStart(commit);
        int count = 0;
        Iterator<RevCommit> it = walk.iterator();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        walk.dispose();
        git.getRepository().close();
        return count;
    }

    /**
     * Delete directory, trying to handle locked files by retrying delete a
     * number of {@code attempts} waiting {@code sleepms} between each attempt.
     * Trouble deleting directories are a typical Windows problem, related to
     * creating a lot of temporary folders and files during functional tests.
     *
     * @param directoryToDelete Full path to directory
     * @param sleepms           milliseconds to sleep between each attempt
     * @param attempts          number of attempt to try to delete the directory
     * @throws IOException
     * @throws InterruptedException
     */
    public static void destroyDirectory(File directoryToDelete, Integer sleepms, Integer attempts) throws IOException, InterruptedException {
        File dir = new File(directoryToDelete.getAbsoluteFile().getAbsolutePath());
        int count = attempts;
        System.out.println("Deleting directory " + dir.toString());
        while (!FileUtils.deleteQuietly(dir)) {
            count--;
            Thread.sleep(sleepms);
            if (count <= 0) {
                System.out.println(String.format("Locked files? Failed to delete directory '%s' for %d seconds (%d tries)", dir.toString(), (sleepms * attempts) / 1000, attempts));
                break;
            }
            System.out.println(String.format("Trying again to deleting directory (try #%s): '%s' ", count, dir.toString()));
        }
    }

    /**
     * Delete directory, trying to handle locked files by retrying for 30 seconds.
     * Trouble deleting directories are a typical Windows problem, related to
     * creating a lot of temporary folders and files during functional tests.
     *
     * @param directoryToDelete Full path to directory
     * @throws IOException
     * @throws InterruptedException
     */
    public static void destroyDirectory(File directoryToDelete) throws IOException, InterruptedException {
        TestUtilsFactory.destroyDirectory(directoryToDelete, 300, 50);
    }

    /**
     * Delete repository from file system To let file handles be released first,
     * it tries several times until max 20 seconds.
     *
     * @param repository
     * @throws IOException
     * @throws InterruptedException
     */
    public static void destroyRepo(Repository repository) throws IOException, InterruptedException {
        if (repository != null) {
            repository.close();
            File repositoryPath = repository.getDirectory().getAbsoluteFile();
            File repositoryWorkDir = new File(repository.getDirectory().getAbsolutePath().replace(".git", ""));
            System.out.println("Attempting to destroy git repository: " + repositoryPath.toString());
            if (repository.getDirectory().exists()) {
                System.out.println("Destroying repo " + repositoryPath.toString());
                /**
                 * This 'hack' has been implemented because the
                 * test-harness/JGit not always releasing the repositories (on
                 * Windows). Tries to delete until success or time-out (after 20
                 * seconds.
                 */
                TestUtilsFactory.destroyDirectory(repositoryPath);
            }

            System.out.println("Attempting to destroy git repository workdir: " + repositoryWorkDir.toString());
            if (repositoryWorkDir.exists()) {
                System.out.println("Destroying repository workdir: " + repositoryWorkDir.toString());
                Git gitrepo = Git.open(repositoryWorkDir);
                gitrepo.close();
                TestUtilsFactory.destroyDirectory(repositoryWorkDir);
            }
        }
    }

    public static void destroyRepo(Git repository) throws IOException, InterruptedException {
        if (repository != null) {
            repository.close();
            File repositoryPath = repository.getRepository().getDirectory().getAbsoluteFile();
            System.out.println("Attempting to destroy git repository: " + repositoryPath.toString());
            if (repositoryPath.exists()) {
                System.out.println("Destroying repo " + repositoryPath.toString());
                /**
                 * This 'hack' has been implemented because the
                 * test-harness/JGit not always releasing the repositories (on
                 * Windows). Tries to delete until success or time-out (after 20
                 * seconds.
                 */
                TestUtilsFactory.destroyDirectory(repositoryPath);
            }
        }
    }

    /**
     * <h3>Destroys a repository</h3>
     * <p>
     * That is close it with JGit and deleting the working
     * directory</p>
     *
     * @param repos List of repositories to close and delete.
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public static void destroyRepo(Repository... repos) throws IOException, InterruptedException {
        for (Repository repository : repos) {
            TestUtilsFactory.destroyRepo(repository);
        }
    }

    public static boolean branchExists(Repository repository, String branch) throws GitAPIException {
        Git git = new Git(repository);

        List<Ref> call = git.branchList().call();

        ListIterator<Ref> refListIterator = call.listIterator();

        while (refListIterator.hasNext()) {
            String branchName = refListIterator.next().getName();
            if (branchName.endsWith(branch)) {
                return true;
            }
        }

        return false;
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)), null, true);
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo, boolean runOnSlave, String integrationBranch) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)), null, runOnSlave, integrationBranch);
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, String repo, boolean runOnSlave, String integrationBranch) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig(repo, null, null, null)), null, runOnSlave, integrationBranch);
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo, boolean runOnSlave) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)), null, runOnSlave);
    }

    public static void triggerProject(AbstractProject<?, ?> project) throws Exception {
        project.getTriggers().clear();
        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);
        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, boolean runOnSlave) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, repoList, repoName, runOnSlave, "master");

    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, boolean runOnSlave, String integrationBranch) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        if (runOnSlave) {
            DumbSlave onlineSlave = rule.createOnlineSlave();
            project.setAssignedNode(onlineSlave);
        }


        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PretestedIntegrationAsGitPluginExt(type == STRATEGY_TYPE.SQUASH ? new SquashCommitStrategy() : new AccumulatedCommitStrategy(), integrationBranch, repoName));
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);


        project.setScm(gitSCM);
        project.save();

        return project;
    }

    //TODO: Create a realistic setup with multi SCM pluging...this seems boiler platey
    public static FreeStyleProject configurePretestedIntegrationPluginWithMultiSCM(JenkinsRule rule, TestUtilsFactory.STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, Repository repo) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<>();
        gitSCMExtensions.add(new PretestedIntegrationAsGitPluginExt(type == STRATEGY_TYPE.SQUASH ? new SquashCommitStrategy() : new AccumulatedCommitStrategy(), "master", repoName));
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        SCM gitSCM1 = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        List<SCM> scms = new ArrayList<>();
        scms.add(gitSCM1);

        MultiSCM scm = new MultiSCM(scms);
        project.setScm(scm);

        return project;
    }

    //This method need to be removed, and exchanged with the method above. A lot of tests fail when this method is removed in various classes
    //This will be done in a separate commit
    @Deprecated
    public static FreeStyleProject configurePretestedIntegrationPluginWithMultiSCM(JenkinsRule rule, TestUtilsFactory.STRATEGY_TYPE type, List<SCM> scms, String repoNamePluginConfig) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        GitBridge gitBridge;
        if (type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), "master", repoNamePluginConfig);
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), "master", repoNamePluginConfig);
        }

        //project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));


        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        MultiSCM scm = new MultiSCM(scms);
        project.setScm(scm);

        return project;
    }

    private static String createCommitMessageForRepo(String repositoryRootFolder, String branch, String message) {
        return String.format("%s-%s-%s", message, branch, repositoryRootFolder);
    }

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

    /**
     * Creates a bare git repository with initial commit and a 'readme.md' file
     * containing one line. Author and email is set on commit.
     *
     * @param repoFolderName
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public static Repository createRepoWithoutBranches(String repoFolderName) throws IOException, GitAPIException {
        //'git init --bare test.git' :
        //Initialized empty Git repository in /home/bue/gitlab-repos/pretested-integration-plugin/test.git/
        //'ls -al test.git/' :
        //total 40
        //drwxrwxr-x  7 doe usr 4096 dec 11 00:23 .
        //drwxrwxr-x 12 doe usr 4096 dec 11 00:23 ..
        //drwxrwxr-x  2 doe usr 4096 dec 11 00:23 branches
        //-rw-rw-r--  1 doe usr   66 dec 11 00:23 config
        //-rw-rw-r--  1 doe usr   73 dec 11 00:23 description
        //-rw-rw-r--  1 doe usr   23 dec 11 00:23 HEAD
        //drwxrwxr-x  2 doe usr 4096 dec 11 00:23 hooks
        //drwxrwxr-x  2 doe usr 4096 dec 11 00:23 info
        //drwxrwxr-x  4 doe usr 4096 dec 11 00:23 objects
        //drwxrwxr-x  4 doe usr 4096 dec 11 00:23 refs
        File repo = new File(repoFolderName + ".git"); // bare repo should have suffix .git, and contain what normally in .git

        File workDirForRepo = new File(repoFolderName);

        System.out.format(workDirForRepo.getAbsolutePath());

        if (repo.exists()) {
            System.out.format("EXIST:" + repo.getAbsolutePath());
            try {
                TestUtilsFactory.destroyDirectory(repo);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo, "readme.md");
        if (!readme.exists()) {
            FileUtils.writeStringToFile(readme, "#My first repository");
        }

        git.add().addFilepattern(".");
        CommitCommand commit = git.commit();
        commit.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "Initial commit"));
        commit.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commit.call();

        git.push().setPushAll().setRemote("origin").call();

        git.close();

        FileUtils.deleteDirectory(workDirForRepo);

        return repository;
    }

    public static Repository createValidRepository(String repoFolderName) throws IOException, GitAPIException {
        File repo = new File(repoFolderName + ".git"); // bare repo should have suffix .git, and contain what normally in .git

        if (repo.exists()) {
            System.out.format("EXIST:" + repo.getAbsolutePath());
            try {
                TestUtilsFactory.destroyDirectory(repo);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        File workDirForRepo = new File(repoFolderName);
        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        String FEATURE_BRANCH_NAME = "ready/feature_1";

        File readme = new File(workDirForRepo + "/readme");
        if (!readme.exists()) {
            FileUtils.writeStringToFile(readme, "sample text\n");
        }

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, git.getRepository().getBranch(), "commit message 1")).call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, git.getRepository().getBranch(), "commit message 2")).call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, git.getRepository().getBranch(), "feature 1 commit 1"));
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, git.getRepository().getBranch(), "feature 1 commit 2"));
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.push().setPushAll().call();

        git.checkout().setName("master").call();

        FileUtils.deleteDirectory(workDirForRepo);
        return repository;
    }

    /**
     * @param repoDir
     * @param commits
     * @return a new repository created using the given list of commits.
     * @throws java.io.IOException
     * @throws org.eclipse.jgit.api.errors.GitAPIException
     */
    public static Repository createRepository(String repoDir, List<TestCommit> commits) throws IOException, GitAPIException {
        File repo = new File(repoDir + ".git");
        if (repo.exists()) {
            System.out.println("The repository already exists: " + repo.getAbsolutePath() + " -> Destroy it");
            try {
                destroyDirectory(repo);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        File worktree = new File(repoDir);
        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(worktree)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        Git git = Git.open(worktree);

        boolean firstCommit = true;
        for (TestCommit commit : commits) {
            if (!commit.branch.equals("master")) {
                boolean branchExists = git.getRepository().getRef(commit.branch) != null;
                if (!branchExists) {
                    git.branchCreate().setName(commit.branch).call();
                }
            }

            if (!firstCommit) {
                git.checkout().setName(commit.branch).call();
            }
            FileUtils.writeStringToFile(new File(git.getRepository().getWorkTree() + "/" + commit.file), commit.content, true);
            git.add().addFilepattern(commit.file).call();
            git.commit().setMessage(commit.message).setAuthor(AUTHOR_NAME, AUTHOR_EMAIL).call();
            firstCommit = false;
        }

        git.push().setPushAll().call();
        git.checkout().setName("master").call();
        FileUtils.deleteDirectory(worktree);
        return repository;
    }

    public static Repository createRepositoryWithMergeConflict(String repoFolderName) throws IOException, GitAPIException {
        String FEATURE_BRANCH_NAME = "ready/feature_1";

        File repo = new File(repoFolderName + ".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);
        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo + "/readme");
        if (!readme.exists()) {
            FileUtils.writeStringToFile(readme, "sample text\n");
        }

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n");

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        FileUtils.writeStringToFile(readme, "Merge conflict branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("merge conflict message 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.push().setPushAll().call();

        FileUtils.deleteDirectory(workDirForRepo);

        return repository;
    }

    public static Repository createValidRepositoryWith2FeatureBranches(String repoFolderName) throws IOException, GitAPIException {
        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        File repo = new File(repoFolderName + ".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);
        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo, "readme");
        if (!readme.exists()) {
            FileUtils.writeStringToFile(readme, "sample text\n");
        }

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_1_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_1_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_2_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_2_NAME).call();

        String readmeContents = FileUtils.readFileToString(readme);
        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 1\n\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n" + readmeContents);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.push().setPushAll().call();
        git.checkout().setName("master").call();

        FileUtils.deleteDirectory(workDirForRepo);

        return repository;
    }

    public static Repository createRepositoryWith2FeatureBranches1Valid1Invalid(String repoDir) throws IOException, GitAPIException {
        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        File repo = new File(repoDir + ".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoDir);

        Repository repository = new FileRepository(repo);
        repository.create(true);

        Git.cloneRepository().setURI("file:///" + repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo + "/readme");
        if (!readme.exists()) {
            FileUtils.writeStringToFile(readme, "sample text\n");
        }

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_1_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_1_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_2_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_2_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 1\n\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
        commitCommand.call();

        git.push().setPushAll().call();

        git.checkout().setName("master").call();

        FileUtils.deleteDirectory(workDirForRepo);

        return repository;
    }

    /**
     * Check if {@code stringToCheck} is equal to one of the lines in @{code
     * file} Useful for verifying lines are merged in files when testing
     * integrations.
     *
     * @param file          File name
     * @param stringToCheck String, representing a complete line in the file.
     * @return true if line is found, false if not found or exception thrown
     * @throws IOException
     */
    public static boolean checkForLineInFile(File file, String stringToCheck) throws IOException {
        boolean result = false;
        // The resource declared in the try-with-resources statement is a BufferedReader.
        try (BufferedReader inputReader = new BufferedReader(new FileReader(file))) {
            System.out.println("look for!:'" + stringToCheck + "'");
            String nextLine;
            while ((nextLine = inputReader.readLine()) != null) {
                System.out.println("next line:'" + nextLine + "'");
                if (nextLine.equals(stringToCheck)) {
                    result = true;
                    break;
                }
            }
            inputReader.close();

            return result;
        } catch (FileNotFoundException e1) {
            System.out.println(String.format("TestUtilsFactory.checkForLineInFile throwed an exception: %s", e1.toString()));
            return false;
        } catch (IOException ep) {
            System.out.println(String.format("TestUtilsFactory.checkForLineInFile throwed an exception: %s", ep.toString()));
            return false;
        }
    }

    /**
     * Helper function to unzip our git repositories we have created for tests.
     *
     * @param destinationFolder Fully qualified path
     * @param zipFile           Fully qualified filename
     */
    public static void unzipFunction(String destinationFolder, String zipFile) {
        File directory = new File(destinationFolder);

        // if the output directory doesn't exist, create it
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // buffer for read and write data to file
        byte[] buffer = new byte[2048];

        try {
            try (FileInputStream fInput = new FileInputStream(zipFile); ZipInputStream zipInput = new ZipInputStream(fInput)) {

                ZipEntry entry = zipInput.getNextEntry();
                Boolean print = true;
                while (entry != null) {
                    String entryName = entry.getName();
                    File file = new File(destinationFolder + File.separator + entryName);

                    // only print first entry, the zip-file itself
                    if (print) {
                        System.out.println("Unzip file " + entryName + " to " + file.getAbsolutePath());
                        print = false;
                    }

                    // create the directories of the zip directory
                    if (entry.isDirectory()) {
                        File newDir = new File(file.getAbsolutePath());
                        if (!newDir.exists()) {
                            boolean success = newDir.mkdirs();
                            if (success == false) {
                                System.out.println("Problem creating Folder");
                            }
                        }
                    } else {
                        try (FileOutputStream fOutput = new FileOutputStream(file)) {
                            int count = 0;
                            while ((count = zipInput.read(buffer)) > 0) {
                                // write 'count' bytes to the file output stream
                                fOutput.write(buffer, 0, count);
                            }
                        }
                    }
                    // close ZipEntry and take the next one
                    zipInput.closeEntry();
                    entry = zipInput.getNextEntry();
                }

                // close the last ZipEntry
                zipInput.closeEntry();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Pretty prints console output from build log
     *
     * @param build
     * @param buildname - descriptive build name included in the output
     * @return boolean - true matched console like text, else false
     * @throws IOException
     * @throws SAXException
     */
    public static boolean printAndReturnConsoleOfBuild(FreeStyleBuild build, String buildname, JenkinsRule jenkinsRule) throws IOException, SAXException {
        // this outputs loft of HTML garbage... so pretty printing after:
        String console = jenkinsRule.createWebClient().getPage(build, "console").asXml();
        System.out.println("************************************************************************");
        System.out.println("* Relevant part of Jenkins build console (captured with regexp)");
        System.out.println(String.format("* Build %s CONSOLE:", buildname));

        // the pattern we want to search for
        Pattern p = Pattern.compile("<link rel=\"stylesheet\" type=\"text/css\" href=\"/jenkins/descriptor/hudson.console.ExpandableDetailsNote/style.css\"/>"
                + ".*<pre.*>(.*)</pre>", Pattern.DOTALL);
        Matcher m = p.matcher(console);
        // if we find a match, get the group
        if (m.find()) {
            // get the matching group
            String capturedText = m.group(1);

            // print the group
            System.out.format("'%s'%n", capturedText);
            return true;
        } else {
            System.out.format("Didn't match any relevant part of the console%n");
            System.out.format("Writing full log to trace%n");
            System.out.format("************************************************************************%n");
            System.out.format(console + "%n");
            System.out.format("************************************************************************%n");
            return false;
        }
    }
}
