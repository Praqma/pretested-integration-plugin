package org.jenkinsci.plugins.pretestedintegration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;

public abstract class SCMBridgeDescriptor<T extends AbstractSCMBridge> extends Descriptor<AbstractSCMBridge> {

    private final static Logger logger = Logger
			.getLogger(SCMBridgeDescriptor.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

	@Override
    public AbstractSCMBridge newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        logger.entering("SCMBridgeDescriptor", "newInstance", new Object[] {
				req, formData });// Generated code DONT TOUCH! Bookmark: 1e30d894baf6512988d9d46eb27ec72e
		logger.exiting("SCMBridgeDescriptor", "newInstance");// Generated code DONT TOUCH! Bookmark: 9e369ee9697012e856edd1d67de7057c
		return super.newInstance(req, formData);
    }

}
