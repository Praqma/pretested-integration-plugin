package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import java.io.File;
import java.util.Iterator;
import static junit.framework.TestCase.assertTrue;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Abstraction for validating a Jenkins build result. It also automagically
 * cleans up any resources used. (Specifically repositories).
 */
public class BuildResultValidator implements AutoCloseable {

    public AbstractBuild<?, ?> build;
    public Repository repo;
    public Result result;
    public String consoleLog;
    public String[] contents;
    public String[] consolePhrases;
    public boolean retain = false;

    public BuildResultValidator(AbstractBuild<?, ?> build, Repository repo) {
        this.repo = repo;
        this.build = build;
    }

    public BuildResultValidator(AbstractBuild<?, ?> build, Repository repo, String consoleLog) {
        this.repo = repo;
        this.build = build;
        this.consoleLog = consoleLog;
    }

    public BuildResultValidator hasResult(Result result) {
        this.result = result;
        return this;
    }

    public BuildResultValidator hasHeadCommitContents(String... contents) {
        this.contents = contents;
        return this;
    }

    public BuildResultValidator buildLogContains(String... consolePhrases) {
        this.consolePhrases = consolePhrases;
        return this;
    }

    public BuildResultValidator retain() {
        this.retain = true;
        return this;
    }

    public void validate() throws Exception {
        if (consolePhrases != null) {
            validateConsoleMessages();
        }

        if (result != null) {
            validateResult();
        }

        if (contents != null) {
            validateHeadCommitMessage();
        }
    }

    private void validateConsoleMessages() {
        for (String phrase : consolePhrases) {
            assertTrue(consoleLog.contains(phrase));
        }
    }

    private void validateResult() throws ValidationException {
        boolean res = build.getResult().equals(result);
        if (!res) {
            throw new ValidationException(String.format("The results did not match, expected result is %s, actual result is %s", result, build.getResult()));
        }
    }

    private boolean validateHeadCommitMessage() throws ValidationException, Exception {

        File workDirForRepo = new File(TestUtilsFactory.WORKDIR,"work-" + repo.getDirectory().getPath().replace(".git", ""));
        Git.cloneRepository().setURI("file:///" + repo.getDirectory().getAbsolutePath()).setDirectory(workDirForRepo)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDirForRepo);

        git.checkout().setName("master").call();
        RevWalk walk = new RevWalk(repo);

        Iterable<RevCommit> logs = git.log().call();
        Iterator<RevCommit> i = logs.iterator();

        ObjectId head = repo.resolve("HEAD");

        Boolean validated = false;
        String errorMessage = "No head commit found";
        RevCommit commit;
        while (i.hasNext()) {
            commit = walk.parseCommit(i.next());
            if (commit.equals(head)) {
                System.out.println(commit.getFullMessage());
                boolean match = true;
                boolean matched = false;
                for (String s : contents) {
                    matched = true;
                    match &= commit.getFullMessage().contains(s);
                }

                if (!(matched && match)) {
                    validated = false;
                    errorMessage = String.format("The head commit did not match any of the strings specified%ncontents:%n%s", commit.getFullMessage());
                    break;
                    //throw new ValidationException("The head commit did not match any of the strings specified");
                } else {
                    //return true;
                    validated = true;

                }

            }
        }
        //throw new ValidationException("No head commit found");
        git.close();
        FileUtils.deleteDirectory(workDirForRepo);
        if (!validated) {
            throw new ValidationException(errorMessage);
        } else {
            return validated;
        }
    }

    @Override
    public void close() throws Exception {
        if (!retain) {
            TestUtilsFactory.destroyRepo(repo);
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
