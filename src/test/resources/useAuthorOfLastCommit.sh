#!/bin/bash

# setting names and stuff
if [ -z "$1" ]; then
	VERSION=""
else
	VERSION="_$1"
fi
NAME=useAuthorOfLastCommit
REPO_NAME=$NAME$VERSION # used for manual testing of script and re-runs
WORK_DIR=`pwd`

LOG=$WORK_DIR/$REPO_NAME-repo_description.log
echo "# Repository view and commits" >> $LOG
echo "" >> $LOG
echo "Git version:" >> $LOG
git --version >> $LOG
echo "" >> $LOG
echo "Linux:" >> $LOG
uname -a >> $LOG
lsb_release >> $LOG
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
echo "--- git log graph: ---" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG
echo "--- git log one-liner: ---" >> $LOG
git log --pretty=format:" * %t (Author: %an <%ae>) (Committer: %cn <%ce>): %s" >> $LOG
echo "" >> $LOG
echo "" >> $LOG

git push origin master

# Doing two commits - each with different author
BN=twoCommitsBranch
git checkout -b $BN

git config user.name "Praqma Support Author One"
git config user.email "support@praqma.net"

touch testCommit.log
echo "# Test commit log" >> testCommit.log
echo "" >> testCommit.log
echo "Used for adding lines to easily commit something during tests. Commit is done by 'Praqma Support Author One'\n" >> testCommit.log
git add testCommit.log
git commit -m "Added test commit log file - first commit by 'Praqma Support Author One'"

git config user.name "Praqma Support Author Two"
git config user.email "support@praqma.net"

touch testCommit.log
echo "Added a new line to this file, to commit something. Commit is done by 'Praqma Support Author Two'\n" >> testCommit.log
git add testCommit.log
git commit -m "Added test commit log file - second commit by 'Praqma Support Author Two'"

echo "After the two commits on the branch:" >> $LOG
echo "-----------------------------------" >> $LOG
echo "git log one-liner:" >> $LOG
git log --pretty=format:"%t (Author: %an <%ae>) (Committer %cn <%ce>): %s" >> $LOG
echo "--- git log graph: ---" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG
echo "--- git log one-liner: ---" >> $LOG
git log --pretty=format:" * %t (Author: %an <%ae>) (Committer: %cn <%ce>): %s" >> $LOG
echo "" >> $LOG
echo "" >> $LOG

# also push to ready branch, so integration can start during the test
git push origin $BN:ready/$BN

echo "Final repository, view after push to ready-branch:" >> $LOG
echo "-----------------------------------" >> $LOG
echo "git log one-liner:" >> $LOG
git log --pretty=format:"%t (Author: %an <%ae>) (Committer %cn <%ce>): %s" >> $LOG
echo "--- git log graph: ---" >> $LOG
git log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative >> $LOG
echo "" >> $LOG
echo "" >> $LOG
echo "--- git log one-liner: ---" >> $LOG
git log --pretty=format:" * %t (Author: %an <%ae>) (Committer: %cn <%ce>): %s" >> $LOG
echo "" >> $LOG
echo "" >> $LOG

# Post process

cd $WORK_DIR
zip -r $NAME$VERSION.zip $REPO_NAME.git
rm -rf $REPO_NAME.git $REPO_NAME

