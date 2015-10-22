package org.jenkinsci.plugins.pretestedintegration;

import javaposse.jobdsl.dsl.DslScriptException;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.Test;

import static org.junit.Assert.*;

public class PretestedIntegrationJobDslExtensionTest {

    @Test
    public void testDslWithSquash() throws Exception {
        Object node = new PretestedIntegrationJobDslExtension().pretestedIntegration("SQUASHED", "branch", "repo");
        assertThat(node, instanceOf(PretestedIntegrationBuildWrapper.class));
        PretestedIntegrationBuildWrapper wrapper = (PretestedIntegrationBuildWrapper) node;

        assertThat(wrapper.scmBridge, instanceOf(GitBridge.class));
        GitBridge bridge = (GitBridge) wrapper.scmBridge;

        assertThat(bridge.integrationStrategy, instanceOf(SquashCommitStrategy.class));
        assertEquals(bridge.getBranch(), "branch");
        assertEquals(bridge.getRepoName(), "repo");
    }

    @Test
    public void testDslWithAccumulated() throws Exception {
        Object node = new PretestedIntegrationJobDslExtension().pretestedIntegration("ACCUMULATED", "branch", "repo");
        assertThat(node, instanceOf(PretestedIntegrationBuildWrapper.class));
        PretestedIntegrationBuildWrapper wrapper = (PretestedIntegrationBuildWrapper) node;

        assertThat(wrapper.scmBridge, instanceOf(GitBridge.class));
        GitBridge bridge = (GitBridge) wrapper.scmBridge;

        assertThat(bridge.integrationStrategy, instanceOf(AccumulatedCommitStrategy.class));
        assertEquals(bridge.getBranch(), "branch");
        assertEquals(bridge.getRepoName(), "repo");
    }

    @Test(expected = DslScriptException.class)
    public void testDslWithInvalidStrategy() throws Exception {
        new PretestedIntegrationJobDslExtension().pretestedIntegration("b0rk", "branch", "repo");
    }
}
