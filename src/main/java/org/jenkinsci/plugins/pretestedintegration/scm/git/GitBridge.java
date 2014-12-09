package org.jenkinsci.plugins.pretestedintegration.scm.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    
    /**
     * Pretested repository configuration field 'Repository name'.
     * @return 
     */
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
     * @param out
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
    
    /**
     * Checks a list of git build data for relevant build data and extracts
     * the relevant single build data to integrate.
     * <ul>
     * <li>Checks extract only build data belonging to the integration repository</li>
     * <li>Check ensures that identical git build data are narrowed down to different sets 
     *      as MultScm and the Git plugin may sometimes contribute with several identical sets</li>
     * <li>Checks ensure that only one relevant set is supplied, or else we fail in case om ambiguity. </li>
     * </ul>
     * TODO: Currently there is no check for branch complies with branch specifier, or that commits (if not given branches) are heads. See JENKINS-25542, JENKINS-25512, JENKINS-24909
     * @param data - git build data
     * @return 
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException if none of the git build data matches chosen integration repository
     * @throws org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException if there is ambiguity about which git build data set to chose to integrate
     */
    public BuildData checkAndDetermineRelevantBuildData(List<BuildData> data) throws NothingToDoException, UnsupportedConfigurationException {
        if(data.isEmpty()) {
            throw new NothingToDoException("No Git SCM change found.");
        }     
        
        Set<BuildData> relevantBuildData = new HashSet<BuildData>();
        
        // Using this HashSet only to detech duplicates
        Set<String> revs = new HashSet<String>();
        
        // An example on several BuilData - visualized can be found in 'docs/More_than_1_gitBuild_data.png'
        for(BuildData bdata : data) {
            // Assume no trailing slash in configuration - we won't match then.
            if(bdata.lastBuild.revision.getBranches().iterator().next().getName().startsWith(resolveRepoName()+"/")) {
                // No we now the git build data contain a branch that matches the integration repository name.
                // Eg. Branch 'origin/ready/feature_1' matches 'origin' configured as integration repository
                
                // Check we have not earlier seen this changeset before,
                // if SHA is the same, it will not be added to the HashSet.
                // We have to use strings, as 'revision' is different objects.
                boolean added = revs.add(bdata.lastBuild.revision.getSha1String());
                if(!added) {
                    //Noting that revision %s has duplicate entry in  (INFO)
                    logger.log(Level.INFO, String.format("checkAndDetermineRelevantBuildData - Nothing that revision %s has duplicate BuildaData entry, using first found", bdata.lastBuild.revision.getSha1String()));
                } else {
                    relevantBuildData.add(bdata); //bdata is an object, so unique and always able to added
                }
            }
        }
        // If no build data added, there is none relevant - so nothing to do.
        // Just building a nice log message here.
        if(relevantBuildData.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for(BuildData d : data) {
                builder.append(String.format(d.lastBuild.revision.getSha1String()+"%n"));
                for(Branch b : d.lastBuild.revision.getBranches()) {
                    builder.append(String.format(b.getName()+"%n"));
                }
            }
            throw new NothingToDoException(String.format("No revision matches configuration in 'Integration repository'%n%s", builder.toString()));
        } else if(relevantBuildData.size() > 1) {
            StringBuilder builder = new StringBuilder();
            for(BuildData d : data) {
                builder.append(String.format(d.lastBuild.revision.getSha1String()+"%n"));
                for(Branch b : d.lastBuild.revision.getBranches()) {
                    builder.append(String.format(b.getName()+"%n"));
                }
            }
            logger.log(Level.SEVERE, String.format("checkAndDetermineRelevantBuildData - Found ambiguius build data (git changes) where both repositori names are the same, but there are more than one change to integrate. Found the following:'%n%s", builder.toString()));
             throw new UnsupportedConfigurationException(UnsupportedConfigurationException.AMBIGUIUITY_IN_BUILD_DATA);
        } else {
            return relevantBuildData.iterator().next();
        }                                    
    }

    @Override
    public void isApplicable(AbstractBuild<?, ?> build, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {        
        List<BuildData> bdata = build.getActions(BuildData.class);
        BuildData data = checkAndDetermineRelevantBuildData(bdata);
    }
    
    @Override
    public void deleteIntegratedBranch(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws DeleteIntegratedBranchException, NothingToDoException, UnsupportedConfigurationException {
        logger.entering("GitBridge", "deleteIntegratedBranch", new Object[] { build, listener, launcher });
		BuildData gitBuildData = checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));
        
        //At this point in time the lastBuild is also the latests. So thats what we use
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
                logger.exiting("GitBridge", "deleteIntegratedBranch");
				throw new DeleteIntegratedBranchException(String.format( "Failed to delete the remote branch %s with the following error:%n%s", gitDataBranch.getName(), out.toString()) );
            } 
        }
    }

    @Override
    public void updateBuildDescription(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws NothingToDoException, UnsupportedConfigurationException {        
        logger.entering("GitBridge", "updateBuildDescription", new Object[] { build, listener, launcher }); 
		BuildData gitBuildData = checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));
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
        
        @Override
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
        
        /**
         * We don't need to verify if we're using git scm, since we will never create ambiguity in remote names 
         * because the plugin renames them if they clash.
         */
        if(project.getScm() instanceof GitSCM) {
            return;
        } else if (Jenkins.getInstance().getPlugin("multiple-scms") != null && project.getScm() instanceof MultiSCM ) {
            MultiSCM multiscm = (MultiSCM)project.getScm();            
            validateMultiScm(multiscm.getConfiguredSCMs());                  
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
    private boolean validateMultiScm(List<SCM> scms) throws UnsupportedConfigurationException {
        Set<String> remoteNames = new HashSet<String>();
        
        for(SCM scm : scms) {
            if(scm instanceof GitSCM) {        
                List<UserRemoteConfig> configs = ((GitSCM)scm).getUserRemoteConfigs();

                for(UserRemoteConfig config : configs) {
                    if(StringUtils.isBlank(config.getName())) {
                        throw new UnsupportedConfigurationException(UnsupportedConfigurationException.MULTISCM_REQUIRE_EXPLICIT_NAMING);
                    }
                    
                    if(!remoteNames.add(config.getName())) {
                        throw new UnsupportedConfigurationException(UnsupportedConfigurationException.AMBIGUIUTY_IN_REMOTE_NAMES);
                    }
                }
            }
        }
        
        return true;
    }

    @Override
    public void handlePostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        updateBuildDescription(build, launcher, listener);

        // The purpose of this section of code is to disallow usage of the master branch as the polling branch.
        BuildData gitBuildData = checkAndDetermineRelevantBuildData(build.getActions(BuildData.class));
        
        // TODO: Implement robustness, in which situations does this one contain multiple revisons, when two branches point to the same commit? (JENKINS-24909). Check branch spec before doing anything             
        Branch gitDataBranch = gitBuildData.lastBuild.revision.getBranches().iterator().next();
        
        // TODO: This master branch check should be moved to job configuration check method.
        String devBranchName = gitDataBranch.getName();
        if (devBranchName.contains("master")) {
            listener.getLogger().println(LOG_PREFIX + "Using the master branch for polling and development is not" +
                    " allowed since it will attempt to merge it to other branches and delete it after.");
            build.setResult(Result.FAILURE);
        }

        Result result = build.getResult();
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
