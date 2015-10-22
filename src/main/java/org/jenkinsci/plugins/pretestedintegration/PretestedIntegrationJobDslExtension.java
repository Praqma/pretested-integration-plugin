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

/*
```
job{
  wrappers{
  	pretestedIntegration(String integrationStrategy, String branch, String repository)
  }
}
```

Valid values for `integrationStrategy` are 'ACCUMULATED' and 'SQUASHED'.

```
job("pi-job"){
  wrappers{
  	pretestedIntegration("SQUASHED","master","origin")
  }
}
```
*/

@Extension(optional = true)
public class PretestedIntegrationJobDslExtension extends ContextExtensionPoint {

    final List<String> strategies = Arrays.asList("ACCUMULATED", "SQUASHED");

    @RequiresPlugin(id = "pretested-integration", minimumVersion = "2.3.0")
    @DslExtensionMethod(context = WrapperContext.class)
    public Object pretestedIntegration(String strategy, String branch, String repo) {
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
        return new PretestedIntegrationBuildWrapper(new GitBridge(integrationStrategy, branch, repo));
    }

    @RequiresPlugin(id = "pretested-integration", minimumVersion = "2.3.3")
    @DslExtensionMethod(context = PublisherContext.class)
    public Object pretestedIntegration() {
        return new PretestedIntegrationPostCheckout();
    }
}
