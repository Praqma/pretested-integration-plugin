#!/bin/bash

# setting names and stuff
if [ -z "$1" ]; then
	VERSION=""
else
	VERSION="_$1"
fi
NAME=commitMessagesWithDoubleQuotes_linux
REPO_NAME=$NAME$VERSION # used for manual testing of script and re-runs
WORK_DIR=`pwd`

LOG=$WORK_DIR/$REPO_NAME-repo_description.log
echo "# Repository view and commits" >> $LOG
echo "" >> $LOG
echo "Git version:" >> $LOG
git --version >> $LOG
echo "" >> $LOG


mkdir -v $REPO_NAME.git
cd $REPO_NAME.git
git init --bare

cd $WORK_DIR
git clone $REPO_NAME.git
cd $REPO_NAME
git config user.name "Praqma Support"
git config user.email "support@praqma.net"

touch README.md
echo "# README of repository $REPO_NAME" >> README.md
echo "" >> README.md
echo "This is a test repository for functional tests." >> README.md
git add README.md
git commit -m "Initial commit - added README"


echo "After initial commit on master:" >> $LOG
echo "-------------------------------" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG

git push origin master

# custom parts
BN=JENKINS-27662_doublequotes
git checkout -b dev/$BN
touch testCommit.log
echo "# Test commit log" >> testCommit.log
echo "" >> testCommit.log
echo "Used for adding lines to commit something during tests.\n" >> testCommit.log
git add testCommit.log
git commit -m "Added test commit log file"

# Problematic commit message with double quotes

echo "Added a new line to this file, to commit something. Commit message will have double quotes" >> testCommit.log
git add testCommit.log
git commit -m 'This is a commit message with double quotes, eg. "test quotes".'
git push origin dev/$BN

echo "After the two commit on dev branch:" >> $LOG
echo "-----------------------------------" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG

# also push to ready branch, so integration can start during the test
git push origin dev/$BN:ready/$BN

echo "Final repository, view dev branch after push to ready-branch:" >> $LOG
echo "------------------------------------------------------------------------------" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG

# Post process

cd $WORK_DIR
zip -r $NAME$VERSION.zip $REPO_NAME.git
rm -rf $REPO_NAME.git $REPO_NAME

