package org.jenkinsci.plugins.pretestedintegration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;

public abstract class SCMBridgeDescriptor<T extends AbstractSCMBridge> extends Descriptor<AbstractSCMBridge> {

	@Override
    public AbstractSCMBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
		return super.newInstance(req, formData);
    }

}
