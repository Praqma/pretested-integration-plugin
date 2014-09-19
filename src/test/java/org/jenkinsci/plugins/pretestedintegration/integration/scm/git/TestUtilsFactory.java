/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;

/**
 *
 * @author Mads
 */
public class TestUtilsFactory {
    public enum STRATEGY_TYPE { SQUASH, ACCUMULATED };
    public static final File GIT_DIR = new File("test-repo/.git");    
    public static final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();
    private static final String README_FILE_PATH = GIT_PARENT_DIR.getPath().concat("/" + "readme");
    private static final String AUTHER_NAME = "john Doe";
    private static final String AUTHER_EMAIL = "Joh@praqma.net";
    
    public static FreeStyleProject configurePretestedIntegrationPlugin(FreeStyleProject project, TestUtilsFactory.STRATEGY_TYPE type, Repository repo) throws IOException, ANTLRException, InterruptedException {
        return configurePretestedIntegrationPlugin(project, type, Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory(), null, null, null)), null);
    }
       
    public static FreeStyleProject configurePretestedIntegrationPlugin(FreeStyleProject project, TestUtilsFactory.STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName) throws IOException, ANTLRException, InterruptedException {
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

        //TODO: We need to remove the origin part from here, but the other tests fail if that is removed.
        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);

        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();

        Thread.sleep(1000);

        return project;
    }
    
    //TODO: Create a realistic setup with multi SCM pluing...this seems boilerplatey
    public static FreeStyleProject configurePretestedIntegrationPluginWithMultiSCM(FreeStyleProject project, TestUtilsFactory.STRATEGY_TYPE type, List<UserRemoteConfig> repoList, String repoName, Repository repo) throws Exception {
        
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
        
        SCM gitSCM1 = new GitSCM(Collections.singletonList(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), null, null, null)),
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        
        MultiSCM scm = new MultiSCM(Arrays.asList(gitSCM1));
        project.setScm(scm);
        
        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);

        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();

        Thread.sleep(1000);
        return project;
    }
    
    public static Repository createValidRepository(String repoFolderName) throws IOException, GitAPIException {        
        File repo = new File(repoFolderName+"/"+".git");
        Repository repository;
        Git git;
        String readmeFileContents_fromDevBranch;
        
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

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
        return repository;
    }
    
    public static Repository createRepositoryWithMergeConflict(String repoFolderName) throws IOException, GitAPIException {
        File repo = new File(repoFolderName+"/"+".git");
        Repository repository;
        Git git;
        String readmeFileContents_fromDevBranch;
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

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
        return repository;
    }
}
