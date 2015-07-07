# Test repository with commits for issue JENKINS-28640

Problem is double quotes in commit messages, are wrongly escaped in commands.

Issue [JENKINS-28640 Quotationmarks in commit message leads to merge failure](https://issues.jenkins-ci.org/browse/JENKINS-28640) report a possible bug, if using double quotes in a commit message.

An earlier issue, namely the [JENKINS-27662](https://issues.jenkins-ci.org/browse/JENKINS-27662) didn't find such problems. It can be related to either our test environment or the way we created the commit messges in those test repositories used in the functional tests.


This repository was created by one of the users of the plugin, a customer of Praqma, and supplies a test example that can reproduce the problem.

There is no script for reproducing the repository, it was created under Windows.

The repository have been converted to a bare git repository, to use it with the tests.
