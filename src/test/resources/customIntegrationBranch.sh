#!/bin/bash


# setting names and stuff
if [ -z "$1" ]; then
	VERSION=""
else
	VERSION="_$1"
fi
NAME=customIntegrationBranch # used below as custom integration branch name also!
REPO_NAME=$NAME$VERSION # used for manual testing of script and re-runs
WORK_DIR=`pwd`

LOG=$WORK_DIR/$REPO_NAME-repo_description.log
# print out method to log
function doPrintOut {
echo "--------------------------------------------------------------------------------" >> $LOG
echo "--- git log graph: ---" >> $LOG
git log --graph --abbrev-commit --decorate --format=format:'%C(bold blue)%h%C(reset) - %C(bold cyan)%aD%C(reset) %C(bold green)(%ar)%C(reset)%C(bold yellow)%d%C(reset)%n'' %C(white)%s%C(reset) %C(dim white)- %an%C(reset)' --all >> $LOG

echo "" >> $LOG
echo "" >> $LOG
echo "--- git log: ---" >> $LOG
git log >> $LOG
echo "" >> $LOG
echo "" >> $LOG
}

echo "###############################################################################" >> $LOG
echo "# Repository creation log and tools version for this test repository" >> $LOG
echo "###############################################################################" >> $LOG


echo "" >> $LOG
echo "Git version:" >> $LOG
git --version >> $LOG
echo "" >> $LOG
echo "Linux:" >> $LOG
uname -a >> $LOG
lsb_release >> $LOG


echo "" >> $LOG
echo "" >> $LOG
echo "###############################################################################" >> $LOG
echo "# Creating basis repository" >> $LOG
echo "###############################################################################" >> $LOG

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
git commit -m "Initial commit on master branch - added README"
sleep 1 # to make sure commit get different second time-stamps

echo "" >> README.md
echo "Second line in readme file" >> README.md
git add README.md
git commit -m "Second commit on on master branch - updated README"
sleep 1 # to make sure commit get different second time-stamps

git push origin master

echo "After basis repository and push of master branch to origin:" >> $LOG
doPrintOut





echo "" >> $LOG
echo "" >> $LOG
echo "###############################################################################" >> $LOG
echo "# Now creating a branch, that will serve as our custom integration branch..." >> $LOG
echo "###############################################################################" >> $LOG

git checkout -b $NAME
echo "" >> README.md
echo "Added a custom integration branch based on master branch" >> README.md
git add README.md
git commit -m "Updated readme file on new custom integration branch"
sleep 1 # to make sure commit get different second time-stamps

git push origin $NAME
echo "After custom integration branch and push to origin:" >> $LOG
doPrintOut


echo "" >> $LOG
echo "" >> $LOG
echo "###############################################################################" >> $LOG
echo "# Adding a commit on master, after we started to use custom integration branch " >> $LOG
echo "#  to test with a more complex git graph" >> $LOG
echo "###############################################################################" >> $LOG

git checkout master

echo "" >> README.md
echo "Last line in readme file" >> README.md
git add README.md
git commit -m "Last line in readme, added from last commit on master. We integrate to another branch from here on."
sleep 1 # to make sure commit get different second time-stamps

git push origin master

echo "After last push to origin of master branch:" >> $LOG
doPrintOut



echo "" >> $LOG
echo "" >> $LOG
echo "###############################################################################" >> $LOG
echo "# Starting on development branch which will be pushed to ready and " >> $LOG
echo "#  integrated by the Jenkins job later" >> $LOG
echo "# Doing two commits, based on the custom integration branch as starting point" >> $LOG
echo "###############################################################################" >> $LOG

git checkout $NAME # checkout custom integration to base development on...
BN=myDevelopmentBranch
git checkout -b $BN

touch testCommit.log
echo "# Test commit log" >> testCommit.log
echo "" >> testCommit.log
echo "Used for adding lines to easily commit something during tests.\n" >> testCommit.log
git add testCommit.log
git commit -m "Added line from $BN in test commit log file."
sleep 1 # to make sure commit get different second time-stamps

echo "Used for adding lines to easily commit something during tests.\n" >> testCommit.log
git add testCommit.log
git commit -m "Added a second line from $BN in test commit log file."
sleep 1 # to make sure commit get different second time-stamps

# also push to ready branch, so integration can start during the test
git push origin $BN:ready/$BN
echo "After push of the two commits on the development branch:" >> $LOG
doPrintOut

echo "" >> $LOG
echo "" >> $LOG
echo "###############################################################################" >> $LOG
echo "# Printing some final views of the repository" >> $LOG
echo "###############################################################################" >> $LOG
echo "" >> $LOG
echo "" >> $LOG
git checkout $NAME
echo "View of repository from integration branch:" >> $LOG
echo "--------------------------------------------------------------------------------" >> $LOG
echo "--- git log graph: ---" >> $LOG
git log --graph --abbrev-commit --decorate --format=format:'%C(bold blue)%h%C(reset) - %C(bold cyan)%aD%C(reset) %C(bold green)(%ar)%C(reset)%C(bold yellow)%d%C(reset)%n'' %C(white)%s%C(reset) %C(dim white)- %an%C(reset)' --all >> $LOG

echo "" >> $LOG
echo "" >> $LOG
git checkout $BN
echo "View of repository from development branch:" >> $LOG
echo "--------------------------------------------------------------------------------" >> $LOG
echo "--- git log graph: ---" >> $LOG
git log --graph --abbrev-commit --decorate --format=format:'%C(bold blue)%h%C(reset) - %C(bold cyan)%aD%C(reset) %C(bold green)(%ar)%C(reset)%C(bold yellow)%d%C(reset)%n'' %C(white)%s%C(reset) %C(dim white)- %an%C(reset)' --all >> $LOG
echo "" >> $LOG

# Post process

cd $WORK_DIR
zip -r $NAME$VERSION.zip $REPO_NAME.git
rm -rf $REPO_NAME.git $REPO_NAME

