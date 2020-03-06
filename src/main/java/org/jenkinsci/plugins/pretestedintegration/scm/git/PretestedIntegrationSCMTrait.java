package org.jenkinsci.plugins.pretestedintegration.scm.git;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import jenkins.plugins.git.traits.GitSCMExtensionTrait;
import jenkins.plugins.git.traits.GitSCMExtensionTraitDescriptor;

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
