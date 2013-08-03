# Pretested Integration 
## Introduction
The Pretested Integration Plugin offers a branchy approach to pretested integration (also known as pre-tested commits), which upholds the invariant; that for a specific branch, known as the integration branch, all commits have been verified.

The plugin delivers an API that makes it possible to easily provide pretested integration functionality for arbitrary SCM tools which is capable of using branches or a similar technology.

The plugin is currently shipped with support for Mercurial as a proof-of-concept implementation and Git implementation is planned as well.

## Under the hood
### Plugin footprint
The plugin manipulates the workspace after changes have been pulled by the SCM and before any build steps are run and after all build steps are run.
Before the build phase, a commit is checked out of the staging branch and merged into the integration branch in order to test compatibility. If the build is marked as stable, the workspace is scanned for further changes. If any changes are found, a new build will be triggered.
Finally, the workspace is cleaned up in order to prepare for the next build.

### Mechanics
An extension point called an SCM interface is used to perform action on the workspace. The internal workflow is as following

1. A build is triggered (e.g. by polling)
2. The Pretested Integration plugin asks the SCM interface for the next commit to be tested, based on the commit tested in the previous build.
3. The Pretested Integration plugin asks the SCM interface to check out the integration branch and merge the found commit leaving the workspace in a state that makes it possible to build and test the changes invoked by the commit.
4. The build phase builds the software
5. The Pretested Integration plugin asks the SCM interface to act on the build result
6. If the build is verified, the commit should be integrated on the integration branch
7. If the build is not verified, the workspace should be rolled back so that another commit can be tested
8. The Pretested Integration plugin asks the SCM interface for the next commit to be tested
9. If any commits are found, a new build is triggered.

### Handling false negatives
It is possible to obtain false negatives in the build phase, causing verifiable commits to be rejected. Resetting the job to rerun tests is an SCM interface implementation and is not handled by the Pretested Integration plugin.

### Something about not_build
If a build is triggered and no commits for integration are found, then the build will be marked as NOT_BUILT

## Setting up pretested integration with Mercurial
### Development workflow
The workflow is adapted from the personal branch version of the branchy approach described in https://wiki.jenkins-ci.org/display/JENKINS/Designing+pre-tested+commit.

1. A named branch is used as the team integration branch (defaults to 'default'). 
2. The developer checks out a feature branch based on the integration branch and commits her changes. (hg update -C default && hg branch feature-branch)
3. Every team member has a designated staging branch (also a named branch) unto where she merges the changes and pushes this branch. (hg update stage-branch && merge feature-branch && hg commit -m "Finished development of feature")
4. Jenkins looks for changes on the stage branch, and integrates verified changes in the integration branch and pushes the updated branch.

### Jenkins setup
For each staging branch, a Jenkins job is configured to poll for changes and trigger a build. A build is created for every found commit, and is sequentially merged into the integration branch if the commit is verified. 
Subsequent jobs can be configured to run further tests, deploy or push the updated integration branch to the repository.

#### Job configuration
1. Under "Source Code Management" select Mercurial. For "Repository URL" use the repository url. Type in the name of the staging branch into "Branch".
2. Under Build Environment, check "Use pretested integration"
3. Select Mercurial and type in the name of the integration branch into "Integration branch".

_Note: A post-build action can also be configured, however it will automatically be activated by the plugin the first time a build is triggered._

#### Reset to latest integrated commit
The Mercurial SCM interface makes it possible to handle false negatives by resetting the internal state to check all subsequent changes not integrated from the last integrated commit in the stage branch history.
This is done by checking the checkbox named "Reset to latest integrated commit" under the Mercurial SCM interface in the job configuration.
False negatives which occur before a successful integration will need to be recommitted to be re-tested and integrated. 

### Currently known issues
- Only builds with Result.STABLE is committed.

## Setting up pretested integration with Git
Not implemented yet

## Extending the Pretested Integration Plugin
An example module is available at https://github.com/rlindsgaard/interface-example

### Creating an SCM interface
To define a new SCM interface, create a public class which extends "org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface" and override the following functions. 

#### Interface methods
##### nextCommit
The method should return an extension of the AbstractCommit<?> class specifying the next commit to be merged and verified calculated from the last commit residing on both integration and staging branch.

##### prepareWorkspace
The method is invoked before the build starts, and after the SCM plugin has downloaded repository changes. 
A branch with the merge of the integration branch and the passed commit should be checked out.

##### handlePostBuild
After the build completes, depending on the build result, the method either integrates the commit being verified or rolls the workspace back preparing for the next build.

_Note: A default implementation exists that invokes commit() or rollback(), so it should it is not necessary to implement this method._

##### commit
Actually merge the commit into the integration branch in the workspace.

##### rollback
If anything needs to be undone, do it here.

### Identifying a commit
For Git and Mercurial, it is possible to uniquely identify a commit by a hash value. It is possible to parameterise "org.jenkinsci.plugins.pretestedintegration.Commit" in order to use a custom class or type which uniquely identifies the commit.