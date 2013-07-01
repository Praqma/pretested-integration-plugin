package org.jenkinsci.plugins.pretestedintegration;

import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;

public abstract class AbstractSCMInterface implements Describable<AbstractSCMInterface>, ExtensionPoint {
	
    public Descriptor<AbstractSCMInterface> getDescriptor() {
        return (SCMInterfaceDescriptor<?>)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<AbstractSCMInterface, SCMInterfaceDescriptor<AbstractSCMInterface>> all() {
        return Jenkins.getInstance().<AbstractSCMInterface, SCMInterfaceDescriptor<AbstractSCMInterface>>getDescriptorList(AbstractSCMInterface.class);
    }
    
    public static List<SCMInterfaceDescriptor<?>> getDescriptors() {
    	List<SCMInterfaceDescriptor<?>> list = new ArrayList<SCMInterfaceDescriptor<?>>();
    	for(SCMInterfaceDescriptor<?> d : all()) {
    		list.add(d);
    	}
    	return list;
    }
}
