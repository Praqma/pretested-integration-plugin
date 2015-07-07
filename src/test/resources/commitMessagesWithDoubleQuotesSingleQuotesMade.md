# Commit message with double quotes made with single quotes


Issue [JENKINS-28640 Quotationmarks in commit message leads to merge failure](https://issues.jenkins-ci.org/browse/JENKINS-28640) report a possible bug, if using double quotes in a commit message.

An earlier issue, namely the [JENKINS-27662](https://issues.jenkins-ci.org/browse/JENKINS-27662) didn't find such problems. It can be related to either our test environment or the way we created the commit messges in those test repositories used in the functional tests.


A new set of test repositories have been created, but on different Windows environments for more variations.
