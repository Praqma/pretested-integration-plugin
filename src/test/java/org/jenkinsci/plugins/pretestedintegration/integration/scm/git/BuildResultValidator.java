/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import java.util.Iterator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 *
 * @author Mads
 */
public class BuildResultValidator implements AutoCloseable {
    
    public AbstractBuild<?,?> build;
    public Repository repo;
    public Result result;
    public String[] contents;
    
    
    public BuildResultValidator(AbstractBuild<?,?> build, Repository repo) {
        this.repo = repo;
        this.build = build;
    }
    
    public BuildResultValidator hasResult(Result result) {
        this.result = result;
        return this;
    }
    
    public BuildResultValidator hasHeadCommitContents(String... contents) {
        this.contents = contents;
        return this;
    }
    
    
    public void validate() throws Exception {       
        if(result != null) {
            validateResult();
        }
        
        if(contents != null) {
            validateHeadCommitMessage();
        }
    }
    
    
    
    private void validateResult() throws ValidationException {
        boolean res = build.getResult().equals(result);
        if(!res) {
            throw new ValidationException(String.format("The results did not match, expected result is %s, actual result is %s", result, build.getResult()));
        }
    }
    
    private boolean validateHeadCommitMessage() throws ValidationException,Exception {        
        Git git = new Git(repo);
        RevWalk walk = new RevWalk(repo);
        
        Iterable<RevCommit> logs = git.log().call();
        Iterator<RevCommit> i = logs.iterator();
        
        ObjectId head = repo.resolve("HEAD");
        
        
        RevCommit commit = null;
        while(i.hasNext()) {
            commit = walk.parseCommit(i.next());
            if(commit.equals(head)) {
                boolean match = true;
                boolean matched = false;
                for(String s : contents) {
                    matched = true;
                    System.out.println(commit.getFullMessage());
                    match &= commit.getFullMessage().contains(s);
                }
                
                if(!(matched && match)) {
                   throw new ValidationException("The head commit did not match any of the strings specified");
                } else {
                    return true;
                }
                   
            } 
        }
        throw new ValidationException("No head commit found");
    }

    @Override
    public void close() throws Exception {
        TestUtilsFactory.destroyRepo(repo);
    }
    
    public class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
 }
