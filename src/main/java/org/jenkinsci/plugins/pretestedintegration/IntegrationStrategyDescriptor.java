package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

public abstract class IntegrationStrategyDescriptor<T extends IntegrationStrategy> extends Descriptor<IntegrationStrategy> {

    public abstract boolean isApplicable(Class<? extends AbstractSCMBridge> bridge);

    @Override
    public IntegrationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return req.bindJSON(IntegrationStrategy.class, formData);
    }

}
