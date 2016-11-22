# Jenkins Pretested Integration Plugin

In this readme file you find _developer oriented documentation_, about contributing, testing, the architecture and different design decisions.

_User oriented documentation_ is on the [Jenkins community wiki plugin page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin).

The [_roadmap_](https://trello.com/b/tOQL6crl/pretested-integration-plugin) is a  public Trello board. While a simple bug, or very simple feature request just can be reported directly on the [Jenkins community issue tracker]( https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27pretested-integration-plugin%27) you should use the _roadmap_ for discussing new ideas, complicated features and the future of the plugin.

Current development efforts are also maintained Kanban-style on the Trello board.


The plugin is maintained in the scope of [Joint Open Source Roadmap Alliance (JOSRA)](http://www.josra.org/) by [Praqma](http://www.praqma.net). We happily accept pull request - see section about contributing below.

# Introduction
The Jenkins Pretested Integration Plugin offers a branchy approach to pretested integration (also known as pretested commits), which upholds the invariant; that for a specific branch, known as the _integration branch_, all commits have been verified.

The plugin delivers an API that makes it possible to easily provide pretested integration functionality for arbitrary SCM tools which is capable of using branches or a similar technology.

The plugin is currently shipped with support for only Git.

The plugin is designed to automate the **[CoDE:U Git Flow](http://www.praqma.com/resources/papers/git-flow)**, but is not bound to a specific SCM work flow.


# References

## Plugin repositories

* [Jenkins CI on Github](https://github.com/jenkinsci/pretested-integration-plugin) (used as final archive for released version)
* [Praqma's on github](https://github.com/Praqma/pretested-integration-plugin)  (used to release from and accept pull requests)


## Automated builds

* **Praqma's build server** (used for daily development builds)
 * [Pretested integration plugin build view](http://code.praqma.net/ci/view/Open%20Source/view/Pretested%20Integration%20Plugin/)
 * [Pretested integration delivery pipeline](http://code.praqma.net/ci/view/Open%20Source%20Pipelines/view/Pretested%20Integration%20Plugin%20-%20build%20pipeline/)
* **Jenkins CI community build** (automated setup for all plugins, not used in our daily workflows)
 *  [Maven project pretested-integration-plugin]((https://jenkins.ci.cloudbees.com/job/plugins/job/pretested-integration-plugin/)



## Roadmap
Roadmap for future development maintained here:

* [Pretested Integration Plugin public Trello board](https://trello.com/b/tOQL6crl/pretested-integration-plugin)


## Wiki and issue tracker
The user oriented documentation is found on the Jenkins community plugin wiki page:

* [Pretested Integration Plugin wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin)

Issues are tracked in the Jenkins JIRA issue tracker:

* [Pretested Integration Plugin - open issues filter]( https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27pretested-integration-plugin%27)


# The Pretested Integration Plugin workflows

The Jenkins community have an [article about designing pretested commits](https://wiki.jenkins-ci.org/display/JENKINS/Designing+pre-tested+commit). The original work on this plugin was inspired from the [personal branch version of the branchy approach](https://wiki.jenkins-ci.org/display/JENKINS/Designing+pre-tested+commit#Designingpre-testedcommit-%22BranchySCM%22approach).

The plugin is designed to automate the **[CoDe:U Git Flow](http://www.praqma.com/resources/papers/git-flow)**, but is not bound to a specific SCM work flow as long as you uses branches.

The recommended workflow are described in our user documentation on the [Pretested Integration Plugin wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin)

Concepts:

* The **ready branch** the developer branch with changes, that should be integrated. It is called ready, because of our recommended naming conventions in the [CoDe:U Git Flow](http://www.praqma.com/resources/papers/git-flow).
* **Ready** branches are specified by configuring the SCM plugin (not the prestested integration plugin) to pic up changes on those specific branches. For the Git plugin, it is the `Branch Specifier`.
* The **integration branch** is the target branch for integration. This is where changes from the ready-branches comes in.

Merge strategies:

**Accumulated** and **Squashed**. These are explained, together with more background information, and a discussion on the different merge strategies in [JOSRA](http://www.josra.org) as a blog post: " [Pretested Integration Plugin](http://www.josra.org/blog/2014/06/23/Pretested%2Bintegration%2Bplugin.html)".

# Architecture

## Plugin phases

The plugin footprint during the build executions have the following phases:

* **Integrate**: Integrates SCM changes _locally_ by manipulating the workspace. Executed after changes have been pulled by the SCM plugin, and before any build steps are run.
* **Publish changes**: As a post-build step, a decision is made based on build result, after all build steps are run, whether to publish the integration.

Before the build phase, a commit is checked out of the _ready_ branch and merged into the _integration branch_ in order to test compatibility. If the build is successfull the integrated changed are published.


## Build results

This plugin can downgrade a build result to:

* `NOT_BUILD`: If a build is triggered and there is not changes for on the _ready_ branch, then the build will be marked as NOT_BUILT
* `FAILED`: If any of the pretested integration plugin phases find a configuration error, or a problem is detected during integration or publish.

The following build results are not changed:

* `FAILED` and `UNSTABLE`: If any build step prior to the integration phase fails, the pretested integration plugin never get control. If build result becomes failed after the integration phase, the plugin never executes the publish phase thus integration is not published.

Publish changes on success:

* `SUCCESS`: If build result successful until the publish changes phase, the integration changes will be published.

# Design decisions

_We currently miss documentation on a lot of the design decisions - they should go into this document._

## The accumulated commit message

The accumulated commit message can not be generated automatically by git, as the squashed message, so the plugin must collect, extract and format the needed information from the commits that goes into the integration commit.
This means that locale settings and language in the environment affect the string formatting regarding dates.
It is an early decision that the accumulated commit message should look the squash commit message.

To make the accumulated commit message look almost identical to the squash message, we use english formatting of the date strings as this seems to be the default behavior for git squash commit message, if autogenerated. Independent from environments.

See the `GetAllCommitsFromBranchCallback` for actual implementation.

Message formatting:

* commit, author and date are indented with spaces to match vertically
* indentation of the individual commit messages (headers and body) is also done with space, not tabs
* date is string formatted using Java simple date formatter, with "EEE MMM d kk:mm:ss yyyy ZZZZ" and English locale.

See the plugin wiki page for more information on example commits and demo jobs: [Demo jobs and example on commit messages and output](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin#PretestedIntegrationPlugin-Demojobsandexampleoncommitmessagesandoutput)

Relates to [JENKINS-29369](https://issues.jenkins-ci.org/browse/JENKINS-29369).

# Only one integration repository is supported

* **Integration only support one repository**: Doing pretested integration on several repositories as the same time would not make sense conceptually. There should also be a 1:1 relation between a Jenkins job and a repository as a best practice. Further it would not be possible to make pretested integration as an atomic non interuptable operation on several repositories. For example if they both integrate successfully, but publishing result fails on the second one. What should then happen with the first one?

# Integration tests

Things you want to know...

* if running the integration tests on Windows, 'git.exe' must be in path.

## Static git repositories

We have been using JGit to create test repositories programatically for the functional tests, which means every test created their own repository and for each test run. This approach works fine, but verifying commits in details can be hard as SHAs, timestamps etc. changes pr. test run. Therefore we have taken an _static git repository_ approach, where we create the reposiories (by script or hand) once, and persist them in the repository as a test resource.

In `src/test/resources/` there is a static git repository collection that can be re-used in tests, or you can create new ones. For details see the file [src/test/resources/howtoTestUsingStaticGitRepos.md](file://src/test/resources/howtoTestUsingStaticGitRepos.md)

**There is a roadmap decision, that every new test should be using static git repositories as preferred setup for working with git repositories during functional tests**.

## Logging

Our strategy for logging in the plugin is to log:

* **Exceptions**: The automated added logging lines logs for almost all exceptions entering and existing exception handling. That is okay, but we need more information than that - human well described situation report pr. exception. Why did we end there? What do we suspect is wrong? Log those informations, as well as stacktrace message and so fort. Also log if we re-throw the exception. Exception must be **`Level.SEVERE`** or worse.
* **State transitions or decisions**: Log situations where we change state or make decisions. Examples can be that we can't find the branch, it does not fulfil requirements, there is no new commit etc.  Typically this is placed where we might print something to console for the user, we want that in the log as well. Use log level **`Level.FINE`** or lower.
* **Every user message or output to Jenkins console**: User messages and console output should be logged as well - it will be way easier to follow those large logs, if the same messages we see in Jenkins job console is in the log as well.
* Log level **Level.WARNING** is typically used for recovered errors.
* Log **"Doing step..."** and *"Done step ..."* so the most important steps, that can fail write to log or console **before** and **after** the step.

Every console or user message must be prefixed with `[PREINT]`, by defining `LOG_PREFIX = "[PREINT] ";` and using it `listener.getLogger().println( String.format(LOG_PREFIX + "Preparing to merge cha ...`.

Use `[PREINT]` only in user messages and console output, as the java loggers know which class that logs already so it will be redundant information.

Example - printing to job console:

        listener.getLogger().println(LOG_PREFIX + "Failed to commit merged changes.");
        listener.getLogger().println(String.format(LOG_PREFIX + "Git command failed with exit code '%d' and error message:", exitCodeCommit));
        listener.getLogger().println(LOG_PREFIX + out.toString());

Example - just logging to java logger:

        logger.fine(String.format("Found remote branch %s", b.getName()));

_This is not yet achieved fully in the plugin_ - but on our roadmap

## Extending the Pretested Integration Plugin

### Contributing

We happily accept pull request on [Praqma's Github repository](https://github.com/Praqma/pretested-integration-plugin)  
This is used to release from, and accepting pull request. **Do not make pull request on [Jenkins CI on Github](https://github.com/jenkinsci/pretested-integration-plugin)** - it is only used as final archive for released versions.

* We don't accept changes, that doesn't reference a Jira issue.
* Every code change must be tested, so there should be either new tests contributed or tests that are changed to new workflows.
* We currently favor functional testing, going through user scenarios in favor of unit tests. Best of course to contribute to both.
* Unless it is a simple bugfix, or feature implementation, please consult the  [Pretested Integration Plugin **roadmap** Trello board](https://trello.com/b/tOQL6crl/pretested-integration-plugin) to discuss implementation ideas.


### Creating an SCM interface
To define a new SCM interface, create a public class which extends "org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface" and overrides relevant methods. Currently we have no guide or howto on this, but the Git implementation should serve as inspiration.


## Acknowledgement

Code contributions were initially made by Computer Science students at University of Copenhagen [DIKU](http://www.diku.dk) as part of a study project.

* Ronni Elken Lindsgaard
* Alexander Winther Uldall
* Esben Skaarup
* Andreas Frisch

### Sponsors

The plugin is primarily developed for our customer - see the [Pretested Integration Plugin wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin)
