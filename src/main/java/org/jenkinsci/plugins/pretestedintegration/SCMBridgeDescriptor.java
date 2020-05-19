package org.jenkinsci.plugins.pretestedintegration;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for SCM Bridges
 * @param <T> Type of the SCM Bridge. Extends AbstractSCMBridge
 */
public abstract class SCMBridgeDescriptor<T extends AbstractSCMBridge> extends Descriptor<AbstractSCMBridge> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractSCMBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return super.newInstance(req, formData);
    }
}
