package org.jenkinsci.plugins.pretestedintegration.unit;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegrationFailedException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitIntegrationStrategy;
import org.junit.Test;

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
            public void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegrationFailedException, NothingToDoException, UnsupportedConfigurationException {
                throw new UnsupportedOperationException("Dummies can't integrate.");
            }
        };
    }
}
