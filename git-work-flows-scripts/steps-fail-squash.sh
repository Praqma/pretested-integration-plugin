#!/bin/bash

BDIR=`pwd`

echo "*******************************************************************************"
echo "* Cleaning up, by removing dirs from last run "
echo "*******************************************************************************"
set -x
rm -rf repo.git repo adams-repo jenkins-repo
set +x

echo "*******************************************************************************"
echo "* Creating repo, and doing initial commit on master branch"
echo "*******************************************************************************"
set -x
git init --bare repo.git
git clone repo.git
cd repo
echo "# Readme" > readme.md
git add readme.md && git commit -m "Initial commit - readme file"
git push origin master
set +x

echo "*******************************************************************************"
echo "* Adams first workflow:"
echo "  1. cloning repo"
echo "  2. creating branch adam"
echo "  3. adding lines to file"
echo "  4. pushing to ready/adam branch"
echo "*******************************************************************************"

set -x
cd $BDIR
git clone repo.git adams-repo
cd adams-repo/

git checkout -b adam
echo "" >> readme.md
echo "This is Adam's line" >> readme.md
git add readme.md && git commit -m "My first line added to readme file"
git push origin adam:ready/adam
set +x


echo "*******************************************************************************"
echo "* Jenkins Pretested Integration workflow"
echo "*******************************************************************************"

set -x
cd $BDIR
rm -rf jenkins-repo/
git clone repo.git jenkins-repo
cd jenkins-repo/
git checkout master
git pull origin master
git merge --squash origin/ready/adam
git commit -m "Integrated origin/ready/adam"
git push origin master
git push origin :ready/adam
set +x

# ... that worked, next time it will fail:


echo "*******************************************************************************"
echo "* Adams second workflow:"
echo "  1. re-using repo and branch, DOES NOT pull first - he is the only one working"
echo "  3. adding lines to file - we know not will merge with last commit"
echo "  4. pushing to ready/adam branch"
echo "*******************************************************************************"

set -x
cd $BDIR
cd adams-repo
echo "# Readmy" > readme.md
echo "" >> readme.md
echo "This is Eve's line" >> readme.md
git add readme.md && git commit -m "Another commit, changed lines"
git push origin adam:ready/adam
set +x


echo "*******************************************************************************"
echo "* Jenkins Pretested Integration workflow"
echo "*******************************************************************************"

set -x
cd $BDIR
rm -rf jenkins-repo/
git clone repo.git jenkins-repo
cd jenkins-repo/
git checkout master
git pull origin master
git merge --squash origin/ready/adam
#git commit -m "Integrated origin/ready/adam"
#git push origin master
#git push origin :ready/adam
set +x



# FAIL
