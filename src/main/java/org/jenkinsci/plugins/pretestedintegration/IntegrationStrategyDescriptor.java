package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Abstract class representing a Descriptor of an Integration Strategy
 *
 * @param <T> The Integration Strategy of this Descriptor
 */
public abstract class IntegrationStrategyDescriptor<T extends IntegrationStrategy> extends Descriptor<IntegrationStrategy> {

    /**
     * @param bridge The SCM Bridge
     * @return Whether or not the Strategy is applicable
     */
    public abstract boolean isApplicable(Class<? extends AbstractSCMBridge> bridge);

    /**
     * {@inheritDoc}
     */


    @Override
    public IntegrationStrategy newInstance(StaplerRequest staplerRequest, JSONObject formData) throws FormException {
        if (staplerRequest != null) {
            staplerRequest.bindJSON(IntegrationStrategy.class, formData);
        }
        return null;
    }

}
