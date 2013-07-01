package org.jenkinsci.plugins.pretestedintegration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.model.Descriptor;

public abstract class SCMInterfaceDescriptor<T extends AbstractSCMInterface> extends
		Descriptor<AbstractSCMInterface> {

    public AbstractSCMInterface newInstance(StaplerRequest req, JSONObject formData) throws FormException {
    	return super.newInstance(req, formData);
    }
   
}
