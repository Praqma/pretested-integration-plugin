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
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;

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
    
    public static void destroyRepo(Repository repository) throws IOException, InterruptedException {
        if(repository != null) {
            repository.close();            
            System.out.println("Attempting to destroy: "+repository.getDirectory().getParentFile().getAbsolutePath());
            if (repository.getDirectory().getParentFile().exists()) {
                System.out.println("Destroying repo "+repository.getDirectory().getParentFile().getAbsolutePath());            
                int attempts = 0;
                /**
                 * This 'hack' has been implmented because i started experiencing the test-harness/jgit not reliquishing
                 * control over my repositories. I created a method that tries to delete the created temporary repository every
                 * 200ms. If it tried 100 times..(20s) and has still not deleted the repository..we fail.                 
                 */ 
                
                while(!FileUtils.deleteQuietly(repository.getDirectory().getParentFile().getAbsoluteFile())) {
                    attempts++;
                    Thread.sleep(200);
                    if(attempts > 100) {
                        throw new InterruptedException("Failed to delete directory after numerous attempts");
                    }
                }
                   
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
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory(), null, null, null)), null, true);
    }
    
    public static FreeStyleProject configurePretestedIntegrationPlugin(JenkinsRule rule, STRATEGY_TYPE type, Repository repo, boolean runOnSlave) throws Exception {
        return configurePretestedIntegrationPlugin(rule, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory(), null, null, null)), null, runOnSlave);
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
    
    public static Repository createRepoWithoutBranches(String repoFolderName) throws IOException, GitAPIException {
        File repo = new File(repoFolderName+"/"+".git");
        File workDirForRepo = new File("work"+repoFolderName);
        
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
        File repo = new File(repoFolderName+"/"+".git");
        Repository repository;
        Git git;

        String FEATURE_BRANCH_NAME = "ready/feature_1";
        
        if (repo.getParentFile().getAbsoluteFile().exists())
            FileUtils.deleteDirectory(repo.getParentFile().getAbsoluteFile());

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(repo.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }
                
        git = new Git(repository);

        File readme = new File(repo.getParent()+"/readme");
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "commit message 1")).call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "commit message 2")).call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n", true);

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "feature 1 commit 1"));
        //commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n", true);

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage(TestUtilsFactory.createCommitMessageForRepo(repoFolderName, repository.getBranch(), "feature 1 commit 2"));
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();        
        return repository;
    }
    
    public static Repository createRepositoryWithMergeConflict(String repoFolderName) throws IOException, GitAPIException {
        File repo = new File(repoFolderName+"/"+".git");
        Repository repository;
        Git git;
        String FEATURE_BRANCH_NAME = "ready/feature_1";

        if (repo.getParentFile().getAbsoluteFile().exists())
            FileUtils.deleteDirectory(repo.getParentFile().getAbsoluteFile());

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(repo.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(repo.getParent()+"/readme");
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
        return repository;
    }
    
    public static Repository createValidRepositoryWith2FeatureBranches(String repoFolderName) throws IOException, GitAPIException {
        File repo = new File(repoFolderName+"/.git");
        Repository repository;
        Git git;
        
        if (repo.getParentFile().exists())
            FileUtils.deleteDirectory(repo.getParentFile().getAbsoluteFile());

        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(repo.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(repo.getParent()+"/readme");
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

        git.checkout().setName("master").call();
        return repository;
    }
    
    public static Repository createRepositoryWith2FeatureBranches1Valid1Invalid(String repoDir) throws IOException, GitAPIException {
        Repository repository;
        Git git;
        File f = new File(repoDir+"/.git");
        if (f.getParentFile().exists())
            FileUtils.deleteDirectory(f.getParentFile().getAbsoluteFile());

        final String FEATURE_BRANCH_1_NAME = "ready/feature_1";
        final String FEATURE_BRANCH_2_NAME = "ready/feature_2";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        git = new Git(repository);

        File readme = new File(repository.getDirectory().getParent()+"/readme");
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

        git.checkout().setName("master").call();
        return repository;
    }
}
