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
    public abstract void integrate(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge, Commit<?> commit) throws IntegationFailedExeception, NothingToDoException;
    
    @DataBoundConstructor
    public IntegrationStrategy() { }

    
    
    public Descriptor<IntegrationStrategy> getDescriptor() {
        return (IntegrationStrategyDescriptor<?>)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    public static DescriptorExtensionList<IntegrationStrategy,IntegrationStrategyDescriptor<IntegrationStrategy>> all() {
        return Jenkins.getInstance().<IntegrationStrategy,IntegrationStrategyDescriptor<IntegrationStrategy>>getDescriptorList(IntegrationStrategy.class);
    }
    
}
