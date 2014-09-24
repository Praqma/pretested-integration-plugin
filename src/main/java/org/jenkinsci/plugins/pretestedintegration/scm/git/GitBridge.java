package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.multiplescms.MultiSCM;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMBridge;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.exceptions.EstablishWorkspaceException;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationAction;
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitChangesFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.DeleteIntegratedBranchException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.RollbackFailureException;
import org.kohsuke.stapler.DataBoundConstructor;

public class GitBridge extends AbstractSCMBridge {

    private String revId;
    private String repoName;

    @DataBoundConstructor
    public GitBridge(IntegrationStrategy integrationStrategy, final String branch, String repoName) {
        super(integrationStrategy);        
        this.branch = branch;
        this.repoName = repoName;
    }    
    
    public GitBridge(IntegrationStrategy integrationStrategy, final String branch) {
        super(integrationStrategy);        
        this.branch = branch;
    }
    
    @Override
    public String getBranch() {
        return StringUtils.isBlank(this.branch) ? "master" : this.branch;
    }

    public String getRevId() {
        return this.revId;
    }

    public void setWorkingDirectory(FilePath workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public FilePath getWorkingDirectory() {
        return this.workingDirectory;
    }

    private GitSCM findScm(AbstractBuild<?, ?> build) throws InterruptedException {
        SCM scm = build.getProject().getScm();

        if(scm instanceof GitSCM) {            
            GitSCM git = (GitSCM) scm;
            return git;
        } else if (Jenkins.getInstance().getPlugin("multiple-scms") != null) {            
            if(scm instanceof MultiSCM) {
                MultiSCM multi = (MultiSCM)scm;
                for(SCM s : multi.getConfiguredSCMs()) {
                    if(s instanceof GitSCM) {
                        return (GitSCM)s;
                    }
                }
                throw new InterruptedException("No git repository configured in multi scm");
            } else {
                throw new InterruptedException("The selected SCM is neither Git nor Multiple SCM");
            }
        } else {
            throw new InterruptedException("You have not selected git as your SCM, and the multiple SCM plugin was not found");
        }            
    }

    private String resolveRepoName() {
        return StringUtils.isBlank(repoName) ? "origin" : repoName;
    }

    private ProcStarter buildCommand(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException {
        GitSCM scm = findScm(build);        
        String gitExe = scm.getGitExe(build.getBuiltOn(), listener);
        ArgumentListBuilder b = new ArgumentListBuilder();
        b.add(gitExe);
        b.add(cmds);
        listener.getLogger().println(String.format("%s %s", PretestedIntegrationBuildWrapper.LOG_PREFIX, b.toStringWithQuote() ));
        return launcher.launch().cmds(b).pwd(build.getWorkspace());
    }

    /**
     * Invoke a command with git
     *
     * @param build
     * @param launcher
     * @param listener
     * @param cmds
     * @return The exitcode of command
     * @throws IOException
     * @throws InterruptedException
     */
    public int git(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException {
        ProcStarter git = buildCommand(build, launcher, listener, cmds);
        int exitCode = git.join();
        return exitCode;
    }

    /**
     * Invoke a command with mercurial
     *
     * @param build
     * @param launcher
     * @param listener
     * @param cmds
     * @return The exitcode of command
     * @throws IOException
     * @throws InterruptedException
     */
    public int git(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener, OutputStream out, String... cmds) throws IOException, InterruptedException {
        ProcStarter git = buildCommand(build, launcher, listener, cmds);
        int exitCode = git.stdout(out).join();
        return exitCode;
    }

    
    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException {
        listener.getLogger().println(String.format("Checking out integration target branch %s and pulling latest changes", getBranch()));
        try {
            //We need to explicitly checkout the remote we have configured
            //TODO: Ask Bue about this...do we really need to do this?          
            //git(build, launcher, listener, "checkout", "-t", resolveRepoName()+"/"+getBranch());
            //git(build, launcher, listener, "checkout", getBranch());
            git(build, launcher, listener, "checkout", "-b", getBranch(), resolveRepoName()+"/"+getBranch());            
            update(build, launcher, listener);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "ensureBranch", ex);
            throw new EstablishWorkspaceException(ex);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "ensureBranch", ex);
            throw new EstablishWorkspaceException(ex);
        }
    }

    protected void update(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {		     
        git(build, launcher, listener, "pull", resolveRepoName(), getBranch());
    }
    
    /**
     * 1. Convert the stuff in the commit to Map<String,String>
     * 2. Check the current working branch if there are any more commits in that
     * branch 3. Check the next branch round-robin
     *
     * @return 
     * @throws NextCommitFailureException
     */
    @Override
    public Commit<String> nextCommit( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws NextCommitFailureException {
        logger.finest("Git plugin, nextCommit invoked");
        Commit<String> next = null;
        try {            
            BuildData gitBuildData = build.getAction(BuildData.class);            
            Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
            logger.fine( String.format( "Found branch with name %s to work on", gitDataBranch.getName()) );
            next = new Commit<String>(gitDataBranch.getSHA1String());
        } catch (Exception e) {            
            logger.finest("Failed to find next commit");
            throw new NextCommitFailureException(e);
        }
        logger.finest("Git plugin, nextCommit returning");
        return next;
    }

    @Override
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitChangesFailureException {
        int returncode = -99999;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            returncode = git(build, launcher, listener, bos, "push", resolveRepoName(), getBranch());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to commit changes to integration branch", ex);
        }
        
        if(returncode != 0) {
            throw new CommitChangesFailureException( String.format( "Failed to commit integrated changes, message was:%n%s", bos.toString()) );
        }
    }

    @Override
    public void rollback(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws RollbackFailureException {        
        int returncode = -9999;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();        
        Commit<?> lastIntegraion = build.getAction(PretestedIntegrationAction.class).getCurrentIntegrationTip();
        try {
            if(lastIntegraion != null) {
                returncode = git(build, launcher, listener, bos, "reset", "--hard", (String)lastIntegraion.getId());
            }         
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to roll back", ex);
        }
        
        //If the return code is -9999 that means no previous pre-test action
        if(returncode != 0 && returncode != -9999) {
            throw new RollbackFailureException( String.format( "Failed to rollback changes, message was:%n%s", bos.toString()) );
        }        
    }

    @Override
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws DeleteIntegratedBranchException {
        BuildData gitBuildData = build.getAction(BuildData.class);
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int delRemote = -99999;
        
        if(build.getResult().isBetterOrEqualTo(getRequiredResult())) {
            try {
                delRemote = git(build, launcher, listener, out, "push", resolveRepoName(),":"+removeOrigin(gitDataBranch.getName()));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failure to delete branch", ex);
            }
            
            if(delRemote != 0) {
                throw new DeleteIntegratedBranchException(String.format( "Failed to delete the remote branch %s with the following error:%n%s", gitDataBranch.getName(), out.toString()) );
            } 
        }
    }

    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        
        BuildData gitBuildData = build.getAction(BuildData.class);
        if(gitBuildData != null) {
            Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();         
            String text = "";
            if(!StringUtils.isBlank(build.getDescription())) {
                text = String.format( "%s<br/>Branch: %s", build.getDescription(), gitDataBranch.getName());
            } else {
                text = String.format( "Branch: %s", gitDataBranch.getName());
            }            
            try {
                build.setDescription(text);
            } catch (Exception ex) { logger.log(Level.FINE, "Failed to update description", ex); /* Dont care */ }  
        }            
        
    }
    
    private String removeOrigin(String branchName) {
        String s = branchName.substring(branchName.indexOf("/")+1, branchName.length());
        return s;
    }
    
    

    @Override
    protected Commit<?> determineIntegrationHead(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) {
        Commit<?> commit = null;
        try {
            GitClient client = Git.with(listener, build.getEnvironment(listener)).in(build.getWorkspace()).getClient();
            for(Branch b : client.getBranches()) {
                if(b.getName().contains(getBranch())) {
                    commit = new Commit(b.getSHA1String());
                }
            }            
        } catch (IOException ex) {
            Logger.getLogger(GitBridge.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(GitBridge.class.getName()).log(Level.SEVERE, null, ex);
        }
        return commit;
    }

    /**
     * @return the remoteName
     */
    public String getRepoName() {
        return repoName;
    }

    /**
     * @param repoName the remoteName to set
     */
    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
    
    @Extension
    public static final class DescriptorImpl extends SCMBridgeDescriptor<GitBridge> {

        public DescriptorImpl() {
            load();
        }
        
        public String getDisplayName() {
            return "Git";
        }
        
        public List<IntegrationStrategyDescriptor<?>> getIntegrationStrategies() {
            List<IntegrationStrategyDescriptor<?>> list = new ArrayList<IntegrationStrategyDescriptor<?>>();
            for(IntegrationStrategyDescriptor<?> descr : IntegrationStrategy.all()) {
                
                if(descr.isApplicable(this.clazz)) {
                    list.add(descr);
                }
            }        
            return list;
        }
        
        public IntegrationStrategy getDefaultStrategy() {            
            return new SquashCommitStrategy();
        }

    }

    private FilePath workingDirectory = null;
    final static String LOG_PREFIX = "[PREINT-GIT] ";
    private static final Logger logger = Logger.getLogger(GitBridge.class.getName());
}
