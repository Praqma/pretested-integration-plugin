package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import javaposse.jobdsl.dsl.RequiresPlugin;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.dsl.helpers.wrapper.WrapperContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;

import java.util.Arrays;
import java.util.List;

import static javaposse.jobdsl.dsl.Preconditions.checkArgument;

/**
 * ExtensionPoint used to support the Jenkins Job DSL.
 * ```
 * job{
 *   wrappers{
 *   	pretestedIntegration(String integrationStrategy, String integrationBranch, String repository)
 *   }
 *   publishers {
 *      pretestedIntegration()
 *   }
 * }
 * ```
 * Valid values for `integrationStrategy` are 'ACCUMULATED' and 'SQUASHED'.
 * ```
 * job('pi-job'){
 *   wrappers{
 *   	pretestedIntegration('SQUASHED','master','origin')
 *   }
 *   publishers {
 *      pretestedIntegration()
 *   }
 * }
 * ```
 */
@Extension(optional = true)
public class PretestedIntegrationAsGitPluginExtJobDslExtension extends ContextExtensionPoint {

    /**
     * Valid options for integrationStrategy
     */
    private final List<String> strategies = Arrays.asList("ACCUMULATED", "SQUASHED");

    /**
     * Method to configure the Pretested Integration wrapper.
     *
     * @param strategy the Integration Strategy to use
     * @param branch the Integration Branch
     * @param repository the repository
     * @param integrationFailedStatusUnstable
     * @return a configured PretestedIntegrationBuildWrapper
     */
    @RequiresPlugin(id = "pretested-integration", minimumVersion = "2.3.0")
    @DslExtensionMethod(context = WrapperContext.class)
    public Object pretestedIntegrationAsGitPluginExt(String strategy, String branch, String repository, boolean integrationFailedStatusUnstable) {
        checkArgument(strategies.contains(strategy), "Strategy must be one of " + strategies);
        IntegrationStrategy integrationStrategy = null;
        switch (strategy) {
            case "ACCUMULATED":
                integrationStrategy = new AccumulatedCommitStrategy();
                break;
            case "SQUASHED":
                integrationStrategy = new SquashCommitStrategy();
                break;
        }
        return new PretestedIntegrationAsGitPluginExt((GitIntegrationStrategy)integrationStrategy, branch, repository, integrationFailedStatusUnstable );
    }

    /**
     * Method to configure the Pretested Integration publisher
     * @return a configured PretestedIntegrationPostCheckout
     */
    @RequiresPlugin(id = "pretested-integration", minimumVersion = "2.3.3")
    @DslExtensionMethod(context = PublisherContext.class)
    public Object pretestedIntegration() {
        return new PretestedIntegrationPostCheckout();
    }
}
