package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import javaposse.jobdsl.dsl.RequiresPlugin;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.dsl.helpers.scm.GitExtensionContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;

import java.util.Arrays;
import java.util.List;

import static javaposse.jobdsl.dsl.Preconditions.checkArgument;

/**
 *ExtensionPoint used to support the Jenkins Job DSL
 *
 *Example:
 *job("generated") {
 *   scm {
 *       git {
 *           remote {
 *               name("origin")
 *               url("some.repo.somewhere.git")
 *           }
 *           extensions {
 *               pretestedIntegration("ACCUMULATED","master","origin")
 *           }
 *       }
 *    }
 *}
 */
@Extension(optional = true)
public class PretestedIntegrationJobDslExtension extends ContextExtensionPoint {

    /**
     * Valid options for integrationStrategy
     */
    private final List<String> strategies = Arrays.asList("ACCUMULATED", "SQUASHED");

    /**
     * Method to configure the Pretested Integration wrapper.
     *
     * @param strategy   the Integration Strategy to use
     * @param branch     the Integration Branch
     * @param repository the repository
     * @return a configured PretestedIntegrationBuildWrapper
     */
    @RequiresPlugin(id = "pretested-integration", minimumVersion = "3.0.0")
    @DslExtensionMethod(context = GitExtensionContext.class)
    public Object pretestedIntegration(String strategy, String branch, String repository) {
        checkArgument(strategies.contains(strategy), "Strategy must be one of " + strategies);
        IntegrationStrategy integrationStrategy = null;
        switch (strategy) {
            case "ACCUMULATED":
                integrationStrategy = new AccumulatedCommitStrategy();
                break;
            case "SQUASHED":
                integrationStrategy = new SquashCommitStrategy();
                break;
            default:
                integrationStrategy = new SquashCommitStrategy();
        }
        return new PretestedIntegrationAsGitPluginExt(integrationStrategy, branch, repository);
    }

    /**
     * Method to configure the Pretested Integration publisher
     *
     * @return a configured PretestedIntegrationPostCheckout
     */
    @RequiresPlugin(id = "pretested-integration", minimumVersion = "3.0.0")
    @DslExtensionMethod(context = PublisherContext.class)
    public Object pretestedIntegration() {
        return new PretestedIntegrationPostCheckout();
    }
}
