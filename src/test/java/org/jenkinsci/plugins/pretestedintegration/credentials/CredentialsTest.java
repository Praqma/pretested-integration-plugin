package org.jenkinsci.plugins.pretestedintegration.credentials;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.PrivateKeySource;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.io.Files;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitException;
import hudson.plugins.git.UserRemoteConfig;
import hudson.util.LogTaskListener;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertTrue;

/**
 * Tests for Credentials support.
 * HOW TO:
 * 1. Generate an SSH key pair.
 * 2. Add the public key to your GitHub account.
 * 3. Drop the private key in the 'keys' directory in the test resources.
 * 4. Rename the private key file to 'credentials'.
 * 5. Run the tests.
 *
 * Notes:
 * When passed faulty credentials, git will go rogue and look in your .ssh directory for a working key.
 * Easiest workaround is temporarily moving your keys.
 *
 * More notes:
 * Tests are excluded by default as they take special preparation and aren't too fast.
 * The 'keys' directory is added to .gitignore so don't worry about pushing your keys.
 * We want to test Pretested Integration works with actual keys and have the tests readily available in the project,
 * but we didn't want to push private keys to the repo, hence having to set up everything yourself.
 */
public class CredentialsTest {

    private final String credentialsId = "pretestedCredentials";
    private final String privateKeyFileName = "credentials";
    private final File privateKeyFile = new File(Thread.currentThread().getContextClassLoader().getResource("keys/" + privateKeyFileName).getPath());
    private final String remoteUrl = "git@github.com:Praqma/pretested-integration-credentials-test.git";
    private final TemporaryDirectoryAllocator tempDirAllocator = new TemporaryDirectoryAllocator();

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    private GitClient setupClient;
    private File repository;
    private LogTaskListener listener;

    @Before
    public void setUp() throws Exception {
        if (!privateKeyFile.exists()) return;

        repository = tempDirAllocator.allocate();
        Logger logger = Logger.getLogger(this.getClass().getPackage().getName());
        logger.setLevel(Level.ALL);
        listener = new hudson.util.LogTaskListener(logger, Level.ALL);
        setupClient = Git.with(listener, new hudson.EnvVars()).in(repository).getClient();
        setupClient.addDefaultCredentials((BasicSSHUserPrivateKey) createPrivateKeyCredentials(credentialsId, privateKeyFile));
        prepareRepository();
    }

    @After
    public void tearDown() {
        if (!privateKeyFile.exists()) return;

        setupClient.clearCredentials();
        tempDirAllocator.disposeAsync();
    }

    @Test
    public void validPrivateKey() throws Exception {
        if (!privateKeyFile.exists()) return;

        Credentials credentials = createPrivateKeyCredentials(credentialsId, privateKeyFile);
        FreeStyleBuild build = runBuildWithCredentials(credentials, "valid private key");

        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatusSuccess(build);
    }

    @Test
    public void invalidPrivateKey() throws Exception {
        if (!privateKeyFile.exists()) return;

        Credentials credentials = createPrivateKeyCredentials(credentialsId, "completely bogus private key");
        FreeStyleBuild build = runBuildWithCredentials(credentials, "invalid private key");

        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue("Build didn't fail due to invalid credentials.", console.contains("Permission denied"));
    }

    @Test
    public void invalidUsernamePassword() throws Exception {
        if (!privateKeyFile.exists()) return;

        Credentials credentials = createUsernamePasswordCredentials("unauthorized-user", "bogusPassword");
        FreeStyleBuild build = runBuildWithCredentials(credentials, "invalid username and password");

        String console = jenkins.createWebClient().getPage(build, "console").asText();
        System.out.println(console);
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertTrue("Build didn't fail due to invalid credentials.", console.contains("Permission denied"));
    }

    private FreeStyleBuild runBuildWithCredentials(Credentials credentials, String commitMessage) throws Exception {
        String readyBranch = "ready/" + UUID.randomUUID().toString().substring(0, 6);

        // Add the credentials
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), credentials);

        FreeStyleBuild build = null;
        try {
            // Prepare a job
            ArrayList<UserRemoteConfig> remotes = new ArrayList<>();
            remotes.add(new UserRemoteConfig(remoteUrl, "origin", "master", credentialsId));
            FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkins, TestUtilsFactory.STRATEGY_TYPE.SQUASH, remotes, "origin", true);

            // Push a branch
            pushTestBranch(readyBranch, commitMessage);

            // Integrate it
            build = project.scheduleBuild2(0).get();
            jenkins.waitUntilNoActivityUpTo(60000);

        } finally {
            store.removeCredentials(Domain.global(), credentials);
            try {
                setupClient.push().ref(":" + readyBranch).to(new URIish(remoteUrl)).force().execute();
            } catch (GitException e) {
                // Branch doesn't exist anymore
            }
        }

        return build;
    }

    private void pushTestBranch(String readyBranch, String message) throws Exception {
        setupClient.checkout().branch(readyBranch).execute();
        File testFile = new File(repository, "credentialsTest");
        FileUtils.writeStringToFile(testFile, "credentialsTest @ " + DateTime.now().toString() + "\n", true);
        setupClient.add("credentialsTest");
        setupClient.commit(message);
        setupClient.push().ref(readyBranch).to(new URIish(remoteUrl)).execute();
    }

    private void prepareRepository() throws Exception {
        setupClient.init_().workspace(repository.getAbsolutePath()).execute();
        setupClient.addRemoteUrl("origin", remoteUrl);
        List<RefSpec> refSpecs = new ArrayList<>();
        refSpecs.add(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        setupClient.fetch_().from(new URIish(remoteUrl), refSpecs).execute();
        setupClient.checkout().ref("master").execute();
    }

    private Credentials createPrivateKeyCredentials(String username, File privateKey) throws Exception {
        return createPrivateKeyCredentials(username, Files.toString(privateKey, Charset.forName("UTF-8")));
    }

    private Credentials createPrivateKeyCredentials(String username, String privateKeyData) throws Exception {
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = username;
        PrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKeyData);
        String passphrase = "";
        String description = "(" + username + ")";
        return new BasicSSHUserPrivateKey(scope, id, username, privateKeySource, passphrase, description);
    }

    private Credentials createUsernamePasswordCredentials(String username, String password) throws Exception {
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = username;
        String description = "(" + username + ")";
        return new UsernamePasswordCredentialsImpl(scope, id, description, username, password);
    }
}
