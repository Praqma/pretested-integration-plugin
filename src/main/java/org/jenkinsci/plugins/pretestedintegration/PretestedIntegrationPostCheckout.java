package org.jenkinsci.plugins.pretestedintegration;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.*;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestTriggerCommitAction;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The publisher determines what will happen when the build has been run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPostCheckout extends Recorder implements Serializable, MatrixAggregatable, SimpleBuildStep {

    final static String LOG_PREFIX = "[PREINT] ";
    private static final Logger LOGGER = Logger.getLogger(PretestedIntegrationPostCheckout.class.getName());

    /**
     * Constructor for PretestedIntegrationPostCheckout
     */
    @DataBoundConstructor
    public PretestedIntegrationPostCheckout() { }

    /**
     * Calls the SCM-specific function according to the chosen SCM. Only called for Non pipeline jobs
     *
     * @param build    The Build
     * @param launcher The Launcher
     * @param listener The BuildListener
     * @return boolean True on success.
     * @throws InterruptedException An foreseen issue
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        AbstractProject<?, ?> proj = build.getProject();
        if (proj instanceof MatrixConfiguration) {
            listener.getLogger().println(LOG_PREFIX + "MatrixConfiguration/sub - skipping publisher - leaving it to root job");
        } else {
            GitSCM scm = (GitSCM)proj.getScm();
            RelativeTargetDirectory rtd = scm.getExtensions().get(RelativeTargetDirectory.class);
            try {
                FilePath wsForUs = rtd != null ? rtd.getWorkingDirectory(scm, proj, build.getWorkspace(), build.getEnvironment(listener), listener) : build.getWorkspace();
                printWarningIfUnsupported(proj.getClass(), listener);
                perform((Run) build, wsForUs, launcher, listener);
            } catch (IOException ex) {
                listener.getLogger().println("[PREINT] FATAL: Unable to determine workspace");
                throw new InterruptedException("[PREINT] FATAL: Unable to determine workspace");
            }
        }
        return true;
    }

    /**
     * For #97. Whitelist certain job types. We include only those we support. We need not include pipeline jobs since
     * they do not call the perform method.
     */
    private static final List<String> WHITELIST = new ArrayList<>();
    static {
        WHITELIST.add(FreeStyleProject.class.getName());
        WHITELIST.add(MatrixProject.class.getName());
        //Maven job type. Several kinds here. We can with 100% certainty say that we match everything with maven here
        WHITELIST.add("hudson.maven");
    }

    public boolean isSupported(String fullClassName) {
        for(String whiteListed : WHITELIST) {
            if (fullClassName.contains(whiteListed)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSupported(Class classname) {
        return isSupported(classname.getName());
    }

    public void printWarningIfUnsupported(Class classname, BuildListener listener) {
        if(!isSupported(classname)) {
            String message = LOG_PREFIX+"Warning: Unsupported job of type '"+classname.getSimpleName()+"'. "+"Pretested Integration Plugin might not work as expected";
            listener.getLogger().println(message);
            LOGGER.log(Level.WARNING, message);
        }
    }

    /**
     * For a matrix project, push should only happen once.
     */
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return PretestedIntegrationPostCheckout.this.perform(build, launcher, listener);
            }
        };
    }


    @Override
    public void perform(Run<?, ?> run, FilePath ws, Launcher launcher, TaskListener listener) throws InterruptedException {
        Result result = run.getResult();

        String triggeredBranch = run.getAction(PretestTriggerCommitAction.class).triggerBranch.getName();
        String integrationBranch = run.getAction(PretestTriggerCommitAction.class).integrationBranch;
        String integrationRepo = run.getAction(PretestTriggerCommitAction.class).integrationRepo;
        String ucCredentialsId = run.getAction(PretestTriggerCommitAction.class).ucCredentialsId;
        
        try {
            // The choice of 'jgit' or 'git'. It must be set though..
            GitClient client = Git.with(listener, run.getEnvironment(listener)).in(ws).using("git").getClient();

            if (ucCredentialsId != null) {
                StandardUsernameCredentials credentials = CredentialsProvider.findCredentialById(ucCredentialsId, StandardUsernameCredentials.class, run, Collections.EMPTY_LIST);
                 
                if (credentials != null) {
                    listener.getLogger().println("[PREINT] Found credentials");
                    client.setCredentials(credentials);
                }
            }

            if (result == null || result.isBetterOrEqualTo(GitBridge.getRequiredResult())) {
                GitBridge.pushToIntegrationBranchGit(run, listener, client, integrationRepo, integrationBranch);
                GitBridge.deleteBranch(run, listener, client, triggeredBranch, integrationRepo);
            } else {
                LOGGER.log(Level.WARNING, "Build result not satisfied - skipped post-build step.");
                listener.getLogger().println(LOG_PREFIX + "Build result not satisfied - skipped post-build step.");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot launch the Git Client.." + ex);
            listener.getLogger().println(LOG_PREFIX + "Cannot launch the Git Client.." + ex);
        }
        GitBridge.updateBuildDescription(run, listener, integrationBranch, triggeredBranch.replace(integrationRepo + "/", ""));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor Implementation for PretestedIntegrationPostCheckout
     */
    @Symbol("pretestedIntegration")
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Pretested Integration publisher to push to integration branch";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
