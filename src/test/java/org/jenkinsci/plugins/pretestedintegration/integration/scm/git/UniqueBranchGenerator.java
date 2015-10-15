package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import java.io.File;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.AUTHOR_EMAIL;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.AUTHOR_NAME;

/**
 * <h3>General purpose class that helps using JGit</h3>
 * <p>
 * Create a unique branch with unique content on master branch when
 * {@link #build()} is called</p>
 */
public class UniqueBranchGenerator {

    public String[] commitMessages;
    public Repository repo;
    public String branchName;

    public UniqueBranchGenerator(Repository repo, String... commitMessages) {
        this.commitMessages = commitMessages;
        this.repo = repo;
    }

    public UniqueBranchGenerator usingBranch(String branchName) {
        this.branchName = branchName;
        return this;
    }

    /**
     * Requires a bare repository. We clone to a random workspace
     *
     * @throws Exception
     * @return The generator currently being worked on
     */
    public UniqueBranchGenerator build() throws Exception {

        Git git;

        File workDirTarget = new File(repo.getDirectory().getAbsolutePath() + "/../../" + UUID.randomUUID().toString());
        System.out.println(workDirTarget.getAbsolutePath());

        Git.cloneRepository()
                .setURI("file://" + repo.getDirectory().getAbsolutePath())
                .setBare(false)
                .setNoCheckout(false)
                .setCloneAllBranches(true)
                .setDirectory(workDirTarget).call().close();

        File gitMetaDir = new File(workDirTarget.getAbsolutePath() + System.getProperty("file.separator") + ".git");
        System.out.println("Setting .git metadata to work in directory: " + gitMetaDir.getAbsolutePath());

        git = Git.open(gitMetaDir);

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(branchName);
        createBranchCommand.call();
        git.checkout().setName(branchName).call();

        File repoRoot = git.getRepository().getWorkTree();
        UUID rand = UUID.randomUUID();
        File randomFile = new File(repoRoot, rand.toString() + ".log");
        randomFile.createNewFile();
        git.add().addFilepattern(".").call();

        int cnt = 0;
        for (String msg : commitMessages) {
            FileUtils.writeStringToFile(randomFile, rand.toString() + "-" + cnt + "\n", true);
            CommitCommand commitCommand = git.commit();
            commitCommand.setMessage(msg);
            commitCommand.setAuthor(AUTHOR_NAME, AUTHOR_EMAIL);
            commitCommand.call();
            cnt++;
        }

        git.push().setPushAll().call();
        git.checkout().setName("master");
        git.close();

        FileUtils.deleteDirectory(workDirTarget);
        return this;
    }
}
