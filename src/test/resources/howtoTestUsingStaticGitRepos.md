# Howto test using static git repositories

**Background**: We have been using JGit to create test repositories programatically for the functional tests, which means every test created their own repository and for each test run. This approach works fine, but verifying commits in details can be hard as SHAs, timestamps etc. changes pr. test run. Therefore we have taken an _static git repository_ approach, where we create the reposiories (by script or hand) once, and persist them in the repository as a test resource.

In `src/test/resources/` there is a static git repository collection that can be re-used in tests, or you can create new ones, where each such repository is a collection of files:

* a **markdown file** describing the repository, how it looks, why, branches etc.
* a **zip archive file**, which is the repository packed as zip
* a **script file** used to create the repository
* a **log file** or **worklog file**, gathering information about the creation of the repository and relevant tools, either output from script or a manual worklog

The **script file** is only used once, when creating the repository (it is not supposed to be re-done), and the **log file** is just for reference.

The **zip archive file** is used in the functional tests as described below and should unpack to a git bare repository.

If you don't wan't to write a script to run it just one, you can _create the repository manually_ - you are then __required to create a worklog markdown file__ documenting your command instead.

**Other files** related to a certain test repository can be put in a folder relating to the repository name.


## Run once, make once - don't change

The git repositories should be re-used in as many tests as possible, but the plan is to create them once, add them and never change them.
We bind tests tight to the specific content of each repository, so changing forces lot of rework in tests.

 
## Examples

## Double quotes test repository

Two repositories have ben created using a run-once script, both script, log-output and the zipped repository for each Windows and Linux have been saved.
This is also the first static git test repository added, the file list in `src/test/resources/` are:

        commitMessagesWithDoubleQuotes_linux-repo_description.log
        commitMessagesWithDoubleQuotes_linux.sh
        commitMessagesWithDoubleQuotes_linux.sh.log
        commitMessagesWithDoubleQuotes_linux.zip
        commitMessagesWithDoubleQuotes.md
        commitMessagesWithDoubleQuotes_windows.bat
        commitMessagesWithDoubleQuotes_windows.bat.log
        commitMessagesWithDoubleQuotes_windows.zip
        howtoTestUsingStaticGitRepos.md

The two zipped git repositories are then used in a test as described below.


## Using test repository in tests

To re-use and simplify using the zipped repositories, we have created a base test class to use in our tests.

Find it in: `src/test/java/org/jenkinsci/plugins/pretestedintegration/integration/scm/git/StaticGitRepositoryTestBase.java`

It have to primary goals:

* **Supply test methods with common setup for using the static git repositories**: Implement the setUp method, inherited by test-classes and used as setup method for each test. The setup method make objects like a bare git repository and a working git repository available. Used respectively in the Jenkins job, and as workspace to verify tests.
* **Contain a map over which test methods using which static git repositories**: We have created a hash map, that for each test method that uses a static git repository specify which git repository zip-file to use from the default test resources. The hashmap serves two purposes: 1) enabling automatic loop-up of zip-file to use in the setUp method to avoid parsing parameters 2) give a simple overview of which repositories are used where (we plan for reuse).


Add your test method by name here, and the static git repository name to use:

        ...
        public class StaticGitRepositoryTestBase {
            
            HashMap<String, String> testMethodName_vs_staticGitRepoName = new HashMap();
            public StaticGitRepositoryTestBase() {
                testMethodName_vs_staticGitRepoName.put(    "commitMessagesWithDoubleQuotesSquashedLinux",              "commitMessagesWithDoubleQuotes_linux");
                testMethodName_vs_staticGitRepoName.put(    "commitMessagesWithDoubleQuotesAccumulatedLinux",           "commitMessagesWithDoubleQuotes_linux");
                testMethodName_vs_staticGitRepoName.put(    "commitMessagesWithDoubleQuotesSquashedWindows",            "commitMessagesWithDoubleQuotes_windows");
                testMethodName_vs_staticGitRepoName.put(    "commitMessagesWithDoubleQuotesAccumulatedWindows",         "commitMessagesWithDoubleQuotes_windows");
            }
        ...
        

Write your test class and test methods without `setUp` and `@Before` - they inherit from `StaticGitRepositoryTestBase` (you must overload if you wan't to change it).

        ...
        public class CommitMessagesWithDoubleQuotes extends StaticGitRepositoryTestBase {
             
            @Test
            public void commitMessagesWithDoubleQuotesSquashedLinux() throws Exception {
        ...
        

In the test you have `bareRepository` available when creating the Jenkins job to refer to

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, bareRepository);

and you can use `gitrepo` as a checked out version:

        // Verify number of commits - first count on master after integration
        gitrepo.checkout().setName("master").call();
        gitrepo.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call(); 
        gitrepo.pull().call();
        int commitsOnMasterAfterIntegration = TestUtilsFactory.countCommitsOnBranch(gitrepo, "master");
        gitrepo.close();