/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Mads
 */
public abstract class SCMPostBuildBehaviour implements Describable<SCMPostBuildBehaviour>, ExtensionPoint {  
    public abstract void applyBehaviour(AbstractBuild build, Launcher launcher, BuildListener listener);
    
    @DataBoundConstructor
    public SCMPostBuildBehaviour() { }

    public Descriptor<SCMPostBuildBehaviour> getDescriptor() {
        return (SCMPostBuildBehaviourDescriptor<?>)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    public static DescriptorExtensionList<SCMPostBuildBehaviour,SCMPostBuildBehaviourDescriptor<SCMPostBuildBehaviour>> all() {
        return Jenkins.getInstance().<SCMPostBuildBehaviour,SCMPostBuildBehaviourDescriptor<SCMPostBuildBehaviour>>getDescriptorList(SCMPostBuildBehaviour.class);
    }
    
}
