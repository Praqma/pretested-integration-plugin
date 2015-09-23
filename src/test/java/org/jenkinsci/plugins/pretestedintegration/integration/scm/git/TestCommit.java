package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

public class TestCommit {

    public final String branch;
    public final String file;
    public final String content;
    public final String message;

    public TestCommit(String branch, String file, String content, String message) {
        this.branch = branch;
        this.file = file;
        this.content = content;
        this.message = message;
    }
}
