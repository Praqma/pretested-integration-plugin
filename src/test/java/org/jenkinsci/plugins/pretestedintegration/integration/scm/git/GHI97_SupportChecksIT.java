package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.junit.Test;

public class GHI97_SupportChecksIT {

    /**
     * See GitHub issue #97
     */
    @Test
    public void validateReturnsFromSupportedChecks_ValidatesGHI97()  {
        PretestedIntegrationPostCheckout pipPost = new PretestedIntegrationPostCheckout();
        FreeStyleProject freeStyleProject = new FreeStyleProject((ItemGroup)null, "FreeStyleProjectMustBeSupported");
        MatrixProject matrix = new MatrixProject(null,"MatrixJobsMustBeSupported");
        assertFalse(pipPost.isSupported("com.tikal.jenkins.plugins.multijob.MultiJobProject"));
        assertTrue(pipPost.isSupported(freeStyleProject.getClass()));
        assertTrue(pipPost.isSupported(matrix.getClass()));
    }
}
