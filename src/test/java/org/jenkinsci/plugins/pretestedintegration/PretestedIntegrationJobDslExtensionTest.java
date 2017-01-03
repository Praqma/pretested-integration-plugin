package org.jenkinsci.plugins.pretestedintegration;

import javaposse.jobdsl.dsl.DslScriptException;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Contains tests for the Jenkins Job DSL support.
 * Test usefulness is fairly limited as it doesn't check the eventual XML output.
 * We just trust the DSL to not mess up the objects we return.
 */
public class PretestedIntegrationJobDslExtensionTest {

    /**
     * Configures Pretested Integration with the Squash strategy
     * @throws Exception when it blows up
     */
    @Test
    public void testDslWithSquash() throws Exception {
        Object node = new PretestedIntegrationJobDslExtension().pretestedIntegration("SQUASHED", "integrationBranch", "repo", false, null);
        assertThat(node, instanceOf(PretestedIntegrationBuildWrapper.class));
        PretestedIntegrationBuildWrapper wrapper = (PretestedIntegrationBuildWrapper) node;

        assertThat(wrapper.scmBridge, instanceOf(GitBridge.class));
        GitBridge bridge = (GitBridge) wrapper.scmBridge;

        assertThat(bridge.integrationStrategy, instanceOf(SquashCommitStrategy.class));
        assertEquals(bridge.getIntegrationBranch(), "integrationBranch");
        assertEquals(bridge.getRepoName(), "repo");
    }

    /**
     * Configures Pretested Integration with the Accumulated strategy
     * @throws Exception when it blows up
     */
    @Test
    public void testDslWithAccumulated() throws Exception {
        Object node = new PretestedIntegrationJobDslExtension().pretestedIntegration("ACCUMULATED", "integrationBranch", "repo", false, null);
        assertThat(node, instanceOf(PretestedIntegrationBuildWrapper.class));
        PretestedIntegrationBuildWrapper wrapper = (PretestedIntegrationBuildWrapper) node;

        assertThat(wrapper.scmBridge, instanceOf(GitBridge.class));
        GitBridge bridge = (GitBridge) wrapper.scmBridge;

        assertThat(bridge.integrationStrategy, instanceOf(AccumulatedCommitStrategy.class));
        assertEquals(bridge.getIntegrationBranch(), "integrationBranch");
        assertEquals(bridge.getRepoName(), "repo");
    }

    /**
     * Configures Pretested Integration with an invalid strategy
     * @throws Exception when it blows up, which it should
     */
    @Test(expected = DslScriptException.class)
    public void testDslWithInvalidStrategy() throws Exception {
        new PretestedIntegrationJobDslExtension().pretestedIntegration("b0rk", "integrationBranch", "repo", false, null);
    }
}
