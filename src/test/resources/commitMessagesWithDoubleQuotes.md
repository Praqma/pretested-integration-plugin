# Commit message with double quotes

Issue [JENKINS-27662](https://issues.jenkins-ci.org/browse/JENKINS-27662) report a possible bug, if using double quotes in a commit message.

This repository is used for testing the scenario where a commit message contain double quotes.

There are two repositories, one created on Linux and on created on Windows, to see if there are any platform dependent issues.

Both script used, are almost identical but one is Bash shell and one is Windows batch script.

Commands used to call the scripts:

`./commitMessagesWithDoubleQuotes_linux.sh >> commitMessagesWithDoubleQuotes_linux.sh.log`

`C:\Users\myuser\Documents>commitMessagesWithDoubleQuotes_windows.bat >> commitMessa
gesWithDoubleQuotes_windows.bat.log`


The linux version logged information about the git repo and commits in file `commitMessagesWithDoubleQuotes_linux-repo_description.log` which is seen below:

## Repository view and commits

Git version:
        git version 1.9.1

After initial commit on master:
-------------------------------

        * 116912c - (HEAD, master) Initial commit - added README (0 seconds ago) <Praqma Support>

After the two commit on dev branch:
-----------------------------------

        * 57807c9 - (HEAD, origin/dev/JENKINS-27662_doublequotes, dev/JENKINS-27662_doublequotes) This is a commit message with double quotes, eg. "test quotes". (0 seconds ago) <Praqma Support>
        * 4ca68de - Added test commit log file (0 seconds ago) <Praqma Support>
        * 116912c - (origin/master, master) Initial commit - added README (0 seconds ago) <Praqma Support>

Final repository, view dev branch after push to ready-branch:
------------------------------------------------------------------------------

        * 57807c9 - (HEAD, origin/ready/JENKINS-27662_doublequotes, origin/dev/JENKINS-27662_doublequotes, dev/JENKINS-27662_doublequotes) This is a commit message with double quotes, eg. "test quotes". (0 seconds ago) <Praqma Support>
        * 4ca68de - Added test commit log file (0 seconds ago) <Praqma Support>
        * 116912c - (origin/master, master) Initial commit - added README (0 seconds ago) <Praqma Support>

