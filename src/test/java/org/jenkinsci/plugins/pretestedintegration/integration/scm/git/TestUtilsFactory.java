/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

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
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Mads
 */
public class TestUtilsFactory {
    
    public enum STRATEGY_TYPE { SQUASH, ACCUMULATED };
    
    public static final File GIT_DIR = new File("test-repo/.git");    

    public static final String AUTHER_NAME = "john Doe";
    public static final String AUTHER_EMAIL = "Joh@praqma.net";

    public static int countCommits(Repository repository) {
        Git git = new Git(repository);
        int commitCount = 0;

        try {
            Iterator<RevCommit> iterator = git.log().call().iterator();
            for ( ; iterator.hasNext() ; ++commitCount ) iterator.next();
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
            for ( ; iterator.hasNext() ; ++commitCount ) iterator.next();
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
            for ( ; iterator.hasNext() ; ++commitCount ) iterator.next();
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
        //System.out.println("Start-Commit: " + commit);
        //System.out.println("Walking all commits starting at HEAD");
        walk.markStart(commit);
        int count = 0;
        for (RevCommit rev : walk) {
            //System.out.println("Commit: " + rev);
            count++;
        }
        //System.out.println(count);
        walk.dispose();
        git.getRepository().close();
        return count;
    }
    
    
    /**
     * Delete directory, trying to handle locked files by retrying delete a number
     * of {@code attempts} waiting {@code sleepms} between each attempt.
     * Trouble deleting directories are a typical Windows problem, related to 
     * creating a lot of temporary folders and files during functional tests.
     * @param directoryToDelete Full path to directory
     * @param sleepms ms to sleep between each attempt
     * @param attempts number of attempt to try to delete the directory
     * @throws IOException
     * @throws InterruptedException 
     */
    public static void destroyDirectory(File directoryToDelete, Integer sleepms, Integer attempts) throws IOException, InterruptedException {
        File dir = new File(directoryToDelete.getAbsoluteFile().getAbsolutePath());
        System.out.println("Deleting directory " + dir.toString());            
                while(!FileUtils.deleteQuietly(dir)) {
                    attempts--;
                    Thread.sleep(sleepms);
                    if(attempts <= 0) {
                        throw new InterruptedException(String.format("Locked files? Failed to delete directory '%s' for %s seconds (%s tries)", dir.toString(), (sleepms*attempts)/1000, attempts));
                    }
                }
    }
     /**
     * Delete directory, trying to handle locked files by retrying for 30 secs.
     * Trouble deleting directories are a typical Windows problem, related to 
     * creating a lot of temporary folders and files during functional tests.
     * @param directoryToDelete Full path to directory
     * @throws IOException
     * @throws InterruptedException 
     */
    public static void destroyDirectory(File directoryToDelete) throws IOException, InterruptedException {
        TestUtilsFactory.destroyDirectory(directoryToDelete, 300, 100);
    }
    
    
    /**
     * Delete repository from file system
     * To let file handles be released first, it tries several times until max
     * 20 seconds.
     * @param repository
     * @throws IOException
     * @throws InterruptedException 
     */
    public static void destroyRepo(Repository repository) throws IOException, InterruptedException {
        if(repository != null) {
            repository.close();            
            File repositoryPath = repository.getDirectory().getAbsoluteFile();
            File repositoryWorkDir = new File(repository.getDirectory().getAbsolutePath().replace(".git", ""));
            System.out.println("Attempting to destroy git repository: " + repositoryPath.toString());
            if (repository.getDirectory().exists()) {
                System.out.println("Destroying repo " + repositoryPath.toString());            
                /**
                 * This 'hack' has been implemented because the test-harness/JGit not always releasing
                 * the repositories (on Windows). Tries to delete until success or time-out (after 20 seconds.
                 */ 
                TestUtilsFactory.destroyDirectory(repositoryPath);
            }
            
            System.out.println("Attempting to destroy git repository workdir: " + repositoryWorkDir.toString());
            if(repositoryWorkDir.exists()) {
                System.out.println("Destroying repository workdir: " + repositoryWorkDir.toString());
                TestUtilsFactory.destroyDirectory(repositoryWorkDir, 300, 100);
            }
        }
    }
    
    /**
     * <h3>Destroys a repository</h3> 
     * <p>That is close it programatically with JGit and deleting the working directory</p>
     * @param repos List of repositories to close and delete.
     */ 
    public static void destroyRepo(Repository... repos) throws IOException, InterruptedException {
        for(Repository repository : repos) {
            TestUtilsFactory.destroyRepo(repository);
        }
    }

    public static boolean branchExists(Repository repository, String branch) throws GitAPIException {
        Git git = new Git(repository);

        List<Ref> call = git.branchList().call();

        ListIterator<Ref> refListIterator = call.listIterator();

        while(refListIterator.hasNext()) {
            String branchName = refListIterator.next().getName();
            if (branchName.endsWith(branch))
                return true;
        }

        return false;
    }

    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)), null, true);
    }
    
    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo, boolean runOnSlave) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)), null, runOnSlave);
    }
    
    public static void triggerProject( FreeStyleProject project  ) throws Exception {        
        project.getTriggers().clear();
        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);
        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();
    }
       
    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, boolean runOnSlave) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        if (runOnSlave) {
            DumbSlave onlineSlave = rule.createOnlineSlave();
            project.setAssignedNode(onlineSlave);
        }

        GitBridge gitBridge = null;

        if(type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), "master", repoName);
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), "master", repoName);
        }

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        return project;
    }
    
    //TODO: Create a realistic setup with multi SCM pluging...this seems boiler platey
    public static FreeStyleProject configurePretestedIntegrationPluginWithMultiSCM(JenkinsRule rule, TestUtilsFactory.STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, Repository repo) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        GitBridge gitBridge = null;
        if(type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), "master", repoName);
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), "master", repoName);
        }
        
        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());
        
        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());
        
        SCM gitSCM1 = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("*/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        List<SCM> scms = new ArrayList<SCM>();
        scms.add(gitSCM1);
        
        MultiSCM scm = new MultiSCM(scms);
        project.setScm(scm);
       
        return project;
    }
    
    public static FreeStyleProject configurePretestedIntegrationPluginWithMultiSCM(JenkinsRule rule, TestUtilsFactory.STRATEGY_TYPE type, List<SCM> scms, String repoNamePluginConfig) throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        GitBridge gitBridge = null;
        if(type == STRATEGY_TYPE.SQUASH) {
            gitBridge = new GitBridge(new SquashCommitStrategy(), "master", repoNamePluginConfig);
        } else {
            gitBridge = new GitBridge(new AccumulatedCommitStrategy(), "master", repoNamePluginConfig);
        }
        
        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        MultiSCM scm = new MultiSCM(scms);
        project.setScm(scm);
       
        return project;
    }
    
    private static String createCommitMessageForRepo(String repositoryRootFolder, String branch, String message) {
        return String.format("%s-%s-%s", message, branch, repositoryRootFolder);
    }
    
    /**
     * Creates a bare git repository with initial commit and a 'readme.md' file
     * containing one line. Author and email is set on commit.
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
        File repo = new File(repoFolderName+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);
        
        Repository repository = new FileRepository(repo);        
        repository.create(true);
        
        Git.cloneRepository().setURI("file:///"+repo.getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)                
                .setNoCheckout(false)
                .call().close();
        
        Git git = Git.open(workDirForRepo);
        
        File readme = new File(workDirForRepo,"readme.md");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "#My first repository");
        
        git.add().addFilepattern(".");
        CommitCommand commit = git.commit();
        commit.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "Initial commit"));
        commit.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commit.call();
        
        git.push().setPushAll().setRemote("origin").call();
        
        git.close();
        
        FileUtils.deleteDirectory(workDirForRepo);
        
        return repository;
    }
    
    public static Repository createValidRepository(String repoFolderName) throws IOException, GitAPIException {                
        File repo = new File(repoFolderName+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);        
        Repository repository = new FileRepository(repo);        
        repository.create(true);

        Git.cloneRepository().setURI("file:///"+repo.getAbsolutePath()).setDirectory(workDirForRepo)
        .setBare(false)
        .setCloneAllBranches(true)                
        .setNoCheckout(false)
        .call().close();
        
        Git git = Git.open(workDirForRepo);
        
        String FEATURE_BRANCH_NAME = "ready/feature_1";
        

        File readme = new File(workDirForRepo+"/readme");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

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
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, git.getRepository().getBranch(), "feature 1 commit 2"));
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();
        
        git.push().setPushAll().call();

        git.checkout().setName("master").call();
        
        FileUtils.deleteDirectory(workDirForRepo);        
        return repository;
    }
    
    public static Repository createRepositoryWithMergeConflict(String repoFolderName) throws IOException, GitAPIException {
        String FEATURE_BRANCH_NAME = "ready/feature_1";
        
        File repo = new File(repoFolderName+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);        
        Repository repository = new FileRepository(repo);        
        repository.create(true);

        Git.cloneRepository().setURI("file:///"+repo.getAbsolutePath()).setDirectory(workDirForRepo)
        .setBare(false)
        .setCloneAllBranches(true)                
        .setNoCheckout(false)
        .call().close();
        
        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo+"/readme");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

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
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        FileUtils.writeStringToFile(readme, "Merge conflict branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("merge conflict message 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();
        
        git.push().setPushAll().call();
        
        FileUtils.deleteDirectory(workDirForRepo);
        
        return repository;
    }

    public static Repository createValidRepositoryWith2FeatureBranches(String repoFolderName) throws IOException, GitAPIException {
        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        File repo = new File(repoFolderName+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoFolderName);        
        Repository repository = new FileRepository(repo);        
        repository.create(true);

        Git.cloneRepository().setURI("file:///"+repo.getAbsolutePath()).setDirectory(workDirForRepo)
        .setBare(false)
        .setCloneAllBranches(true)                
        .setNoCheckout(false)
        .call().close();
        
        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo,"readme");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_1_NAME);
        Ref ref = createBranchCommand.call();
        

        git.checkout().setName(FEATURE_BRANCH_1_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
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
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n" + readmeContents);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.push().setPushAll().call();        
        git.checkout().setName("master").call();
        
        FileUtils.deleteDirectory(workDirForRepo);
        
        return repository;
    }
    
    public static Repository createRepositoryWith2FeatureBranches1Valid1Invalid(String repoDir) throws IOException, GitAPIException {
        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";
        
        File repo = new File(repoDir+".git"); // bare repo should have suffix .git, and contain what normally in .git
        File workDirForRepo = new File(repoDir);
        
        Repository repository = new FileRepository(repo);        
        repository.create(true);

        Git.cloneRepository().setURI("file:///"+repo.getAbsolutePath()).setDirectory(workDirForRepo)
        .setBare(false)
        .setCloneAllBranches(true)                
        .setNoCheckout(false)
        .call().close();
        
        Git git = Git.open(workDirForRepo);

        File readme = new File(workDirForRepo+"/readme");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

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
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
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
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_2 branch commit 2\n\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 2 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();
                
        git.push().setPushAll().call();

        git.checkout().setName("master").call();
        
        FileUtils.deleteDirectory(workDirForRepo);
        
        return repository;
    }
    
    /**
     * Check if {@code stringToCheck} is equal to one of the lines in @{code file}
     * Useful for verifying lines are merged in files when testing integrations.
     * @param file File name
     * @param stringToCheck String, representing a complete line in the file.
     * @return true if line is found, false if not found or exception thrown
     * @throws IOException 
     */
    public static boolean checkForLineInFile(File file, String stringToCheck) throws IOException {
        boolean result = false;
        try {
            BufferedReader inputReader = new BufferedReader(new FileReader(file));
            System.out.println("look for!:'" + stringToCheck + "'");
            String nextLine;
            while ((nextLine = inputReader.readLine()) != null) {
                System.out.println("next line:'" + nextLine + "'");
                if (nextLine.equals(stringToCheck)) {
                    result = true;
                    break;
                }
            }

            return result;
        } catch (FileNotFoundException e1) {
            return false;
        } catch (IOException ep) {
            return false;
        }
    }
    
    
    /**
     * Helper function to unzip our git repositories we have created for tests.
     * @param destinationFolder Fully qualified path
     * @param zipFile Fully qualified filename
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
            FileInputStream fInput = new FileInputStream(zipFile);
            ZipInputStream zipInput = new ZipInputStream(fInput);

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
                    FileOutputStream fOutput = new FileOutputStream(file);
                    int count = 0;
                    while ((count = zipInput.read(buffer)) > 0) {
                        // write 'count' bytes to the file output stream
                        fOutput.write(buffer, 0, count);
                    }
                    fOutput.close();
                }
                // close ZipEntry and take the next one
                zipInput.closeEntry();
                entry = zipInput.getNextEntry();
            }

            // close the last ZipEntry
            zipInput.closeEntry();

            zipInput.close();
            fInput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	     
}