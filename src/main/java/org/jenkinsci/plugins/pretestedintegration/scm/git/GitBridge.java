package org.jenkinsci.plugins.pretestedintegration.scm.git;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
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
import org.jenkinsci.plugins.pretestedintegration.SCMBridgeDescriptor;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategy;
import org.jenkinsci.plugins.pretestedintegration.IntegrationStrategyDescriptor;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.exceptions.CommitChangesFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.DeleteIntegratedBranchException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NextCommitFailureException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;
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
        logger.exiting("GitBridge", "getBranch");// Generated code DONT TOUCH! Bookmark: c5d724b40407cb2f3dfd1e3bbe6e22be
		logger.entering("GitBridge", "getBranch");// Generated code DONT TOUCH! Bookmark: 48744452c8833c010f5e4e411fa98246
		return StringUtils.isBlank(this.branch) ? "master" : this.branch;
    }

    public String getRevId() {
        logger.entering("GitBridge", "getRevId");// Generated code DONT TOUCH! Bookmark: 7daeaf95ed1ab33f362632d94f8d0775
		logger.exiting("GitBridge", "getRevId");// Generated code DONT TOUCH! Bookmark: 05723ee14ce48ed93ffbd8d5d9af889a
		return this.revId;
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

    public void setWorkingDirectory(FilePath workingDirectory) {
        logger.entering("GitBridge", "setWorkingDirectory", new Object[] { workingDirectory });// Generated code DONT TOUCH! Bookmark: f086ef8587d2c20ffb8bf94f954f2248
		this.workingDirectory = workingDirectory;
		logger.exiting("GitBridge", "setWorkingDirectory");// Generated code DONT TOUCH! Bookmark: 11a18b3727f6a7e7712ab3bfd2e3356c
    }

    public FilePath getWorkingDirectory() {
        logger.entering("GitBridge", "getWorkingDirectory");// Generated code DONT TOUCH! Bookmark: 2ca0fd766104faf1fa453e42a3a201ca
		logger.exiting("GitBridge", "getWorkingDirectory");// Generated code DONT TOUCH! Bookmark: 0ae84c2f8bf13225243d846a688f7ff0
		return this.workingDirectory;
    }

    private ProcStarter buildCommand(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException {
        logger.entering("GitBridge", "buildCommand", new Object[] { build, listener, launcher, cmds }); // Generated code DONT TOUCH! Bookmark: 37725f753e9558763e6ad20b5c536cea
		GitSCM scm = findScm(build);        
        String gitExe = scm.getGitExe(build.getBuiltOn(), listener);
        ArgumentListBuilder b = new ArgumentListBuilder();
        b.add(gitExe);
        b.add(cmds);
        listener.getLogger().println(String.format("%s %s", PretestedIntegrationBuildWrapper.LOG_PREFIX, b.toStringWithQuote() ));
        logger.exiting("GitBridge", "buildCommand");// Generated code DONT TOUCH! Bookmark: b2de8fe32eb583d6dac86f020b66bfa4
		return launcher.launch().pwd(resolveWorkspace(build, listener)).cmds(b);
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
        logger.entering("GitBridge", "git", new Object[] { build, listener, launcher, cmds });// Generated code DONT TOUCH! Bookmark: eba75a8277a4ac0774f9ab528c2a21c4
		ProcStarter git = buildCommand(build, launcher, listener, cmds);                
        int exitCode = git.join();
        logger.exiting("GitBridge", "git");// Generated code DONT TOUCH! Bookmark: 7f0667850693f0663168b340f4ea2744
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
        logger.entering("GitBridge", "git", new Object[] { build, out, listener, launcher, cmds });// Generated code DONT TOUCH! Bookmark: 2841cb2de272a4236d3804108235b84a
		ProcStarter git = buildCommand(build, launcher, listener, cmds);
        int exitCode = git.stdout(out).join();
        logger.exiting("GitBridge", "git");// Generated code DONT TOUCH! Bookmark: 7f0667850693f0663168b340f4ea2744
		return exitCode;
    }

    
    @Override
    public void ensureBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, String branch) throws EstablishWorkspaceException {
        logger.entering("GitBridge", "ensureBranch", new Object[] { build, branch, listener, launcher });// Generated code DONT TOUCH! Bookmark: eb203ba8b33b4c38087310c398984c1a
		listener.getLogger().println(String.format("Checking out integration target branch %s and pulling latest changes", getBranch()));
        try {
            //We need to explicitly checkout the remote we have configured            
            //git(build, launcher, listener, "checkout", getBranch(), resolveRepoName()+"/"+getBranch());            
            git(build, launcher, listener, "checkout", "-B", getBranch(), resolveRepoName()+"/"+getBranch());            
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
        try {
            logger.exiting("GitBridge", "ensureBranch - IOException");// Generated code DONT TOUCH! Bookmark: 775c55327ca90d7a3b1889cb1547bc4c
            git(build, launcher, listener, "pull", resolveRepoName(), getBranch());
        } catch (InterruptedException ex) {
            logger.exiting("GitBridge", "ensureBranch - InterruptedException");// Generated code DONT TOUCH! Bookmark: 775c55327ca90d7a3b1889cb1547bc4c
            throw new EstablishWorkspaceException(ex);
        } catch (IOException ex) {
            logger.exiting("GitBridge", "ensureBranch - IOException");// Generated code DONT TOUCH! Bookmark: 775c55327ca90d7a3b1889cb1547bc4c
            throw new EstablishWorkspaceException(ex);
        }
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
        logger.entering("GitBridge", "nextCommit", new Object[] { build, listener, launcher, commit });// Generated code DONT TOUCH! Bookmark: 5f5045d7fcbafdea51208e1a4863fe34
		logger.finest("Git plugin, nextCommit invoked");
        Commit<String> next = null;
        try {            
            BuildData gitBuildData = build.getAction(BuildData.class);            
            Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
            logger.fine( String.format( "Found branch with name %s to work on", gitDataBranch.getName()) );
            next = new Commit<String>(gitDataBranch.getSHA1String());
        } catch (Exception e) {            
            logger.finest("Failed to find next commit");
            logger.exiting("GitBridge", "nextCommit");// Generated code DONT TOUCH! Bookmark: 5db7b4d59db361b0d1c37e3cbd1ab0be
			throw new NextCommitFailureException(e);
        }
        logger.finest("Git plugin, nextCommit returning");
        logger.exiting("GitBridge", "nextCommit");// Generated code DONT TOUCH! Bookmark: 5db7b4d59db361b0d1c37e3cbd1ab0be
		return next;
    }

    @Override
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws CommitChangesFailureException {
        logger.entering("GitBridge", "commit", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 323bfa8523aa7ffbc35f4833c31252a3
		int returncode = -99999;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            returncode = git(build, launcher, listener, bos, "push", resolveRepoName(), getBranch());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to commit changes to integration branch", ex);
        }
        
        if(returncode != 0) {
            logger.exiting("GitBridge", "commit");// Generated code DONT TOUCH! Bookmark: ecae92e6b0686c962ca0233a399fd28f
			throw new CommitChangesFailureException( String.format( "Failed to commit integrated changes, message was:%n%s", bos.toString()) );
        }
    }

    @Override
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {        
        //Check for more than one git build data object. Only observed with Multiple SCMs plugin, when using more than 1 instance of a git scm.
        //Defensive check, multiple scm check handled in checkConfig, but it could happen when used with other plugins.
        //See image docs/pics/More_than_1_gitBuild_data.png      
        if(build.getActions(BuildData.class).size() > 1) {
            throw new UnsupportedConfigurationException(String.format("More than one git build data detected, currently not supported.%nPossible cause: Multiple scm configurations, custom buildswrappers (by other plugins)."));
        }
        
        BuildData gitBuildData = build.getAction(BuildData.class);
        
        //If no build data was contributed
        if(gitBuildData == null) {
            throw new NothingToDoException("Not triggered by Git");
        }
        
        //Check to make sure that we do ONLY integrate to the branches specified.
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();        
        if(!gitDataBranch.getName().startsWith(resolveRepoName()+"/")) {
            throw new NothingToDoException(String.format("The git repository name %s does not match pretested configuration", gitDataBranch.getName()));
        }
    }
    
    @Override
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws DeleteIntegratedBranchException {
        logger.entering("GitBridge", "deleteIntegratedBranch", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 111eed322ec80cb71cbb9dbb4ec42bac
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
                logger.exiting("GitBridge", "deleteIntegratedBranch");// Generated code DONT TOUCH! Bookmark: 6769b00709eba81c7847b15d665987ca
				throw new DeleteIntegratedBranchException(String.format( "Failed to delete the remote branch %s with the following error:%n%s", gitDataBranch.getName(), out.toString()) );
            } 
        }
    }

    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {        
        logger.entering("GitBridge", "updateBuildDescription", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: ebe53ccfc6676ea284a7dcb8855514e3
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
		logger.exiting("GitBridge", "updateBuildDescription");// Generated code DONT TOUCH! Bookmark: 4b67859f8914c1ec862b51d3a63b0c88            
        
    }
    
    private String removeOrigin(String branchName) {
        logger.entering("GitBridge", "removeOrigin", new Object[] { branchName });// Generated code DONT TOUCH! Bookmark: 24143a2abef7179d205e0f671730c3a1
		String s = branchName.substring(branchName.indexOf("/")+1, branchName.length());
        logger.exiting("GitBridge", "removeOrigin");// Generated code DONT TOUCH! Bookmark: 520100b967686459d29a418d2f3b331a
		return s;
    }
    
    public FilePath resolveWorkspace(AbstractBuild<?,?> build, TaskListener listener) throws InterruptedException, IOException {
        logger.entering("GitBridge", "resolveWorkspace");
        FilePath ws = build.getWorkspace();
        GitSCM scm = findScm(build);
        RelativeTargetDirectory rtd = scm.getExtensions().get(RelativeTargetDirectory.class);        
        
        if(rtd != null) {
            ws = rtd.getWorkingDirectory(scm, build.getProject(), ws, build.getEnvironment(listener), listener);
        }
        
        logger.fine("Resolved workspace to "+ws);
        logger.exiting("GitBridge", "resolveWorkspace");                
        return ws;        
    }

    @Override
    protected Commit<?> determineIntegrationHead(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) {
        logger.entering("GitBridge", "determineIntegrationHead", new Object[] { build, listener, launcher });// Generated code DONT TOUCH! Bookmark: 9151bd5b4bfbdc724c67fa8d42a44f57
		Commit<?> commit = null;
        try {
            logger.fine(String.format("About to determine integration head for build, for branch %s", getBranch() ) );
            GitClient client = Git.with(listener, build.getEnvironment(listener)).in(resolveWorkspace(build, listener)).getClient();
            for(Branch b : client.getBranches()) {
                if(b.getName().contains(getBranch())) {
                    logger.fine("Found integration head commit sha: "+b.getSHA1String());
                    commit = new Commit(b.getSHA1String());
                }
            }            
        } catch (IOException ex) {
            Logger.getLogger(GitBridge.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(GitBridge.class.getName()).log(Level.SEVERE, null, ex);
        }
        logger.exiting("GitBridge", "determineIntegrationHead");// Generated code DONT TOUCH! Bookmark: 9a7c5aed5c90867aaf92d7b4e2598e30
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

    @Override
    public void validateConfiguration(AbstractProject<?, ?> project) throws UnsupportedConfigurationException {
        if( project.getScm() instanceof GitSCM ) {
            validateGitScm((GitSCM)project.getScm());
        //we need to ask Jenkins.getInstance().getPlugin() before instnaceof because you would get a class not 
        //not defined on jenkins instances if the vm tried to evalute the instanceof on installations without the 
        //Multiple SCMs plugin.
        } else if(Jenkins.getInstance().getPlugin("multiple-scms") != null && project.getScm() instanceof MultiSCM ) {
            MultiSCM multiscm = (MultiSCM)project.getScm();
 
            for(SCM scm : multiscm.getConfiguredSCMs()) {                
                if(scm instanceof GitSCM) {
                    GitSCM gitMultiScm = (GitSCM)scm;                    
                    validateGitScm(gitMultiScm);
                }
            }
        } else {
            throw new UnsupportedConfigurationException("We only support 'Git' and 'Multiple SCMs' plugins");
        } 
    }
    
    //For JENKINS-24754
    /**
     * Validate the git configuration. We need to make sure that in situations where
     * @param scm
     * @throws UnsupportedConfigurationException 
     */
    private void validateGitScm(GitSCM scm) throws UnsupportedConfigurationException {
        List<UserRemoteConfig> configs = scm.getUserRemoteConfigs();
        //The default git configuration with 1 repository in config. Blank name for remote (defaults to origin) and default value in pretested integration.
        boolean isDefault = configs.size() == 1 && StringUtils.isBlank(configs.get(0).getName()) && resolveRepoName().equals("origin"); 
        
        //If you're not using the standard values.
        if(!isDefault) {               
            for(UserRemoteConfig config : configs) {
                //If the configured remote matches...Or the case where you have multiple repos selected with default config
                //This also covers the scenario where origin is explicitly named in the configuration. 
                if(resolveRepoName().equals(config.getName()) || (resolveRepoName().equals("origin") && StringUtils.isBlank(config.getName()) ) ) {
                    return;
                }
            }                
            throw new UnsupportedConfigurationException(UnsupportedConfigurationException.ILLEGAL_CONFIG_NO_REPO_NAME_DEFINED);
        }        
    }

    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        Result result = build.getResult();
        updateBuildDescription(build, launcher, listener);

        // The purpose of this section of code is to disallow usage of the master branch as the polling branch.
        BuildData gitBuildData = build.getAction(BuildData.class);
        
        // TODO: Implement robustness, in which situations does this one contain multiple revisons, when two branches point to the same commit? (JENKINS-24909). Check branch spec before doing anything             
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        
        String devBranchName = gitDataBranch.getName();
        if (devBranchName.contains("master")) {
            listener.getLogger().println(LOG_PREFIX + "Using the master branch for polling and development is not" +
                    " allowed since it will attempt to merge it to other branches and delete it after.");
            build.setResult(Result.FAILURE);
        }

        if (result != null && result.isBetterOrEqualTo(getRequiredResult())) {

            listener.getLogger().println(LOG_PREFIX + "Commiting changes");                
            commit(build, launcher, listener);
            listener.getLogger().println(LOG_PREFIX + "Deleting development branch");
            deleteIntegratedBranch(build, launcher, listener);            
        } 
    }
    
    
    

    private FilePath workingDirectory = null;
    final static String LOG_PREFIX = "[PREINT-GIT] ";
    private static final Logger logger = Logger.getLogger(GitBridge.class.getName());
}
