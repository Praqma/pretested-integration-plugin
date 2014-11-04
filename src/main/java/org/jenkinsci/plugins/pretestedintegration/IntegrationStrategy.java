/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration;

import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {  
    private final static Logger logger = Logger
			.getLogger(IntegrationStrategy.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4

	public abstract void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge, Commit<?> commit) throws IntegationFailedExeception, NothingToDoException;
    
    @DataBoundConstructor
    public IntegrationStrategy() { }

    
    
    public Descriptor<IntegrationStrategy> getDescriptor() {
        logger.exiting("IntegrationStrategy", "getDescriptor");// Generated code DONT TOUCH! Bookmark: ecd722247263f21a17188169745720f1
		logger.entering("IntegrationStrategy", "getDescriptor");// Generated code DONT TOUCH! Bookmark: 24cc4de9955cf69f2428d18f247547c0
		return (IntegrationStrategyDescriptor<?>)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    public static DescriptorExtensionList<IntegrationStrategy,IntegrationStrategyDescriptor<IntegrationStrategy>> all() {
        logger.entering("IntegrationStrategy", "all");// Generated code DONT TOUCH! Bookmark: b760438f7e1423732caaa9ef553c5f93
		logger.exiting("IntegrationStrategy", "all");// Generated code DONT TOUCH! Bookmark: 4a32ea823412bf7eb75d28dd9edca807
		return Jenkins.getInstance().<IntegrationStrategy,IntegrationStrategyDescriptor<IntegrationStrategy>>getDescriptorList(IntegrationStrategy.class);
    }
    
}
