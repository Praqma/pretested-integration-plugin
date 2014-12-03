# Jenkins Pretested Integration Plugin

In this readme file you find _developer oriented documentation_, about contributing, testing, the architecture and different design decisions.

_User oriented documentation_ is on the [Jenkins community wiki plugin page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin).

The [_roadmap_](https://trello.com/b/tOQL6crl/pretested-integration-plugin) is a  public Trello board.

The plugin is maintained in the scope of [Joint Open Source Roadmap Alliance (JOSRA)](http://www.josra.org/) by [Praqma](http://www.praqma.net). We happily accept pull request - see section about contributing below.

## Introduction
The Jenkins Pretested Integration Plugin offers a branchy approach to pretested integration (also known as pretested commits), which upholds the invariant; that for a specific branch, known as the _integration branch_, all commits have been verified.

The plugin delivers an API that makes it possible to easily provide pretested integration functionality for arbitrary SCM tools which is capable of using branches or a similar technology.

The plugin is currently shipped with support for only Git.

The plugin is designed to automate the **[CoDE:U Git Flow](http://www.praqma.com/resources/papers/git-flow)**, but is not bound to a specific SCM work flow.


## References

### Plugin repositories

* [Jenkins CI on Github](https://github.com/jenkinsci/pretested-integration-plugin) (used as final archive for released version)
* [Praqma's on github](https://github.com/Praqma/pretested-integration-plugin)  (used to release from and accept pull requests)


### Automated builds

* **Praqma's build server** (used for daily development builds)
 * [Pretested integration plugin build view](http://code.praqma.net/ci/view/Open%20Source/view/Pretested%20Integration%20Plugin/)
 * [Pretested integration delivery pipeline](http://code.praqma.net/ci/view/Open%20Source%20Pipelines/view/Pretested%20Integration%20Plugin%20-%20build%20pipeline/)
* **Jenkins CI community build** (automated setup for all plugins, not used in our daily workflows)
 *  [Maven project pretested-integration-plugin]((https://jenkins.ci.cloudbees.com/job/plugins/job/pretested-integration-plugin/)



### Roadmap
Roadmap for future development maintained here:

* [Pretested integration plugin public Trello board](https://trello.com/b/tOQL6crl/pretested-integration-plugin)


### Wiki and issue tracker
The user oriented documentation is found on the Jenkins community plugin wiki page:

* [Pretested Integration Plugin wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin)

Issues are tracked in the Jenkins JIRA issue tracker:

* [Pretested Integration Plugin - open issues filter]( https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27pretested-integration-plugin%27)


## The Pretested Integration Plugin workflows

The Jenkins community have an [article about designing pretested commits](https://wiki.jenkins-ci.org/display/JENKINS/Designing+pre-tested+commit). The original work on this plugin was inspired from the [personal branch version of the branchy approach](https://wiki.jenkins-ci.org/display/JENKINS/Designing+pre-tested+commit#Designingpre-testedcommit-%22BranchySCM%22approach).

The plugin is designed to automate the **[CoDe:U Git Flow](http://www.praqma.com/resources/papers/git-flow)**, but is not bound to a specific SCM work flow as long as you uses branches.

The recommended workflow are described in our user documentation on the [Pretested Integration Plugin wiki page](https://wiki.jenkins-ci.org/display/JENKINS/Pretested+Integration+Plugin)

Concepts:

* The **ready branch** the developer branch with changes, that should be integrated. It is called ready, because of our recommended naming conventions in the [CoDe:U Git Flow](http://www.praqma.com/resources/papers/git-flow).
* **Ready** branches are specified by configuring the SCM plugin (not the prestested integration plugin) to pic up changes on those specific branches. For the Git plugin, it is the `Branch Specifier`.
* The **integration branch** is the target branch for integration. This is where changes from the ready-branches comes in.


## Architecture

### Plugin phases

The plugin footprint during the build executions have the following phases:

* **Integrate**: Integrates SCM changes _locally_ by manipulating the workspace. Executed after changes have been pulled by the SCM plugin, and before any build steps are run.
* **Publish changes**: As a post-build step, a decision is made based on build result, after all build steps are run, whether to publish the integration.

Before the build phase, a commit is checked out of the _ready_ branch and merged into the _integration branch_ in order to test compatibility. If the build is successfull the integrated changed are published.


### Build results

This plugin can downgrade a build result to:

* `NOT_BUILD`: If a build is triggered and there is not changes for on the _ready_ branch, then the build will be marked as NOT_BUILT
* `FAILED`: If any of the pretested integration plugin phases find a configuration error, or a problem is detected during integration or publish.

The following build results are not changed:

* `FAILED` and `UNSTABLE`: If any build step prior to the integration phase fails, the pretested integration plugin never get control. If build result becomes failed after the integration phase, the plugin never executes the publish phase thus integration is not published.

Publish changes on success:

* `SUCCESS`: If build result successful until the publish changes phase, the integration changes will be published.

## Design decisions

_We currently miss documentation on a lot of the design decisions - they should go into this document._

## Logging

Our strategy for logging in the plugin is to:

* **Exceptions**: The automated added logging lines logs for almost all exceptions entering and existing exception handling. That is okay, but I need more information than that - human well described situation report pr. exception. Why did we end there? How ? What do we suspect is wrong. Log those informations, as well as stacktrace message and so fort. Also log if we re-throw the exception.
* **State transitions or decisions**: please log situation where we change state or make decisions. Examples can be that we can't find the branch, it does not fulfil requirements, there is no new commit and so forth. Typically this is placed where we might print something to console for the user, I want that in the log as well.
* **Every user message or output to Jenkins console**: User messages and console output should be logged as well - it will be way easier to follow those large logs, if the same messages we see in Jenkins job console is there as well.

_This is not yet achieved fully in the plugin_

#### Automated inserted logging statements
Praqma have a little java tools called Code-injector that can be used to automatically insert logging statements in java code.

The project is available internally in Praqma.

We have used to add logging to this plugin at a point where there were missing lots of logging.

Automated logging looks like this:

    public String getRevId() {
      logger.entering("GitBridge", "getRevId");// Generated code DONT TOUCH! Bookmark: 7daeaf95ed1ab33f362632d94f8d0775
      logger.exiting("GitBridge", "getRevId");// Generated code DONT TOUCH! Bookmark: 05723ee14ce48ed93ffbd8d5d9af889a
		    return this.revId;
      }

_Notes to use the project internally in Praqma (add/remove automated logging)_:
To run the operation clone the `code-injector` project from Praqma's internall Gitlab, and run `mvn clean install` to add it to the local maven cache.
Next clone both `pretested-integration-plugin` and `usecase-pretested-integration-plugin` from the code-injector namespace.

The java class called `InsertLoggingStatements` contains a path pointing to the `pretested-integration-plugin`, change that as appropriate.
Run the `Main` class and check the results.




## Extending the Pretested Integration Plugin

### Contributing

We happily accept pull request on [Praqma's Github repository](https://github.com/Praqma/pretested-integration-plugin)  
This is used to release from, and accepting pull request.

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
