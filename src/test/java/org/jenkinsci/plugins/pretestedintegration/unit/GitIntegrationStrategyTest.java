package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitIntegrationStrategy;
import org.junit.Test;

import java.io.IOException;

public class GitIntegrationStrategyTest {
    @Test
    public void getPersonIdentTest() throws Exception{
        GitIntegrationStrategy strategy = getDummyStrategy();
        PersonIdent identity = strategy.getPersonIdent("john Doe <Joh@praqma.net> 1442321765 +0200");
        assertNotNull("Shouldn't be null.", identity);
        assertEquals("Identity name mismatch", "john Doe", identity.getName());
        assertEquals("Identity mail mismatch", "Joh@praqma.net", identity.getEmailAddress());
    }

    private GitIntegrationStrategy getDummyStrategy() {
        return new GitIntegrationStrategy() {
            @Override
            public void integrate(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Branch triggeredBranch, GitBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException, IOException, InterruptedException {
                throw new UnsupportedOperationException("Dummies can't integrate.");
            }
        };
    }
}
