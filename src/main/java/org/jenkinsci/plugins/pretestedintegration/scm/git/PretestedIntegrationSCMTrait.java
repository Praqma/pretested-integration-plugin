package org.jenkinsci.plugins.pretestedintegration.scm.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.traits.GitSCMExtensionTrait;
import jenkins.plugins.git.traits.GitSCMExtensionTraitDescriptor;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class PretestedIntegrationSCMTrait extends GitSCMExtensionTrait<PretestedIntegrationAsGitPluginExt> {

    @DataBoundConstructor
    public PretestedIntegrationSCMTrait(PretestedIntegrationAsGitPluginExt extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Use pretested integration";
        }
    }
}
