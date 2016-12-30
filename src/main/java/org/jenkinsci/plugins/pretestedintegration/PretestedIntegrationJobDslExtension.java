package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import java.util.Arrays;
import java.util.List;
import javaposse.jobdsl.dsl.helpers.wrapper.WrapperContext;
import javaposse.jobdsl.dsl.RequiresPlugin;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import org.jenkinsci.plugins.pretestedintegration.scm.git.AccumulatedCommitStrategy;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import static javaposse.jobdsl.dsl.Preconditions.checkArgument;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;

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
public class PretestedIntegrationJobDslExtension extends ContextExtensionPoint {

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
     * @return a configured PretestedIntegrationBuildWrapper
     */
    @RequiresPlugin(id = "pretested-integration", minimumVersion = "2.3.0")
    @DslExtensionMethod(context = WrapperContext.class)
    public Object pretestedIntegration(String strategy, String branch, String repository, boolean integrationFailedStatusUnstable ,String allowedNoCommits) {
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

        return new PretestedIntegrationBuildWrapper(new GitBridge(integrationStrategy, branch, repository, integrationFailedStatusUnstable, Integer.getInteger(allowedNoCommits)));
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
