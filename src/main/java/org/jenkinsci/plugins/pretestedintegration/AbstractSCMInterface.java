package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

public abstract class AbstractSCMInterface implements Describable<AbstractSCMInterface>, ExtensionPoint {

    public SCMInterfaceDescriptor getDescriptor() {
        return (SCMInterfaceDescriptor)Hudson.getInstance().getDescriptor(AbstractSCMInterface.class);
    }

    public static DescriptorExtensionList<AbstractSCMInterface, SCMInterfaceDescriptor> all() {
        return Hudson.getInstance().<AbstractSCMInterface, SCMInterfaceDescriptor>getDescriptorList(AbstractSCMInterface.class);
    }

    public abstract class SCMInterfaceDescriptor extends Descriptor<AbstractSCMInterface> {
        protected SCMInterfaceDescriptor(Class<? extends AbstractSCMInterface> clazz) {
            super(clazz);
        }

        protected SCMInterfaceDescriptor () {

        }
    }
}
