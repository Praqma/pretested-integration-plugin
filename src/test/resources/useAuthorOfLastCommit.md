# Integration commit must have author of last commit

_Test repository for the following feature_:

Both accumulated and squashed strategy sets both committer and author to the git user on the Jenkins slave.

The author on the commits on the development branch should be kept, as the author on the new commit on the integration branch.
This follows the well know pull-request patterns of many OSS project, where the maintainer accepts the pull requests and becomes the commiter, while the original author doing the work get credit as author.

The usual repository browser shows author of commits, thus the history in such systems will be more interesting when looking over the git history. Today it is the git user on the Jenkins slave showing as both committer and author.

* if there is more than one commit on the ready branch, the author of the last commit will be used.


* https://issues.jenkins-ci.org/browse/JENKINS-28590


The test repositories uses different commit authors when creating the repository, thus the tests can verify that the correct author is used when integrating.
