package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor.FormException;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

public abstract class SCMBridgeDescriptor<T extends AbstractSCMBridge> extends Descriptor<AbstractSCMBridge> {

    @Override
    public AbstractSCMBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return super.newInstance(req, formData);
    }
}
