#!/usr/bin/env bash
set -x
set -e

#git clone git@github.com:bicschneider/test-git-phlow-plugin.git refs/tags/init --shallow
rm -rf test-git-phlow-plugin 
mkdir test-git-phlow-plugin
cd test-git-phlow-plugin

git init
git config --add remote.origin.url git@github.com:bicschneider/test-git-phlow-plugin.git
git config --add remote.origin.fetch +refs/heads/*:refs/remotes/origin/*
git config --add branch.master.remote origin
git config --add branch.master.merge refs/heads/master

git checkout -B master
echo "# HEADER" >> README.md
cp `pwd`/../`dirname $0`/JenkinsfileScripted .
cp `pwd`/../`dirname $0`/JenkinsfileDeclarative .
git add .
git commit -m "init"
git tag -a -m "init" init

branch_prefixes="FsExtSq FsExtAcc FsBwAcc FsBwSq FsExtBwAcc MxExtAcc MxExtSq MuExtAcc MuExtSq MvnExtAcc  PipeScriptedSCM PipeScriptedScript PipeDeclSCM PipeDeclScript"
#branch_prefixes="MvnExtAcc"

function resetToInit(){
    if [ "${1}x" == "x" ]; then
       tag="init"
    else
       tag=${1}
    fi
    git checkout master
    git reset --hard ${tag}
    git clean -xfd
}

function checkoutMyBranch(){
    git checkout -B $1
}

function createSimpleTestScenario(){
    local text=$1
    local file=$2
    resetToInit && checkoutMyBranch ready${branch_prefix}/$text
    echo $text >> $file
    git add $file
    git commit -m "$text"
}

for branch_prefix in ${branch_prefixes} ; do
    resetToInit && checkoutMyBranch master${branch_prefix}
done

for branch_prefix in ${branch_prefixes} ; do
    # Place it on to top of
    text="test-01-change-Jenkinsfile_README.dk-ff" && \
        resetToInit && checkoutMyBranch ready${branch_prefix}/$text && \
        echo "println \"$text\"" > JenkinsfileScripted && \
        echo "println \"$text\"" > JenkinsfileDeclarative && \
        cat `pwd`/../`dirname $0`/JenkinsfileScripted >> JenkinsfileScripted && \
        git add JenkinsfileScripted && \
        cat `pwd`/../`dirname $0`/JenkinsfileDeclarative >> JenkinsfileDeclarative && \
        git add JenkinsfileDeclarative && \
        echo $text >> README.md
        git add README.md && \
        git commit -m "$text"
done
git push --mirror
checkoutMyBranch "master" && resetToInit
for branch_prefix in ${branch_prefixes} ; do
  git branch -D master${branch_prefix}
  git branch -D ready${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff
done


#read -n 1 -p "Enter to continue" enter

for branch_prefix in ${branch_prefixes} ; do
    createSimpleTestScenario "test-02-merge-conflicts" README.md
    createSimpleTestScenario "test-03-merge-ok" test.md
    createSimpleTestScenario "test-04-build-failed" build_failed.md
done

for branch_prefix in ${branch_prefixes} ; do
  resetToInit && checkoutMyBranch ready${branch_prefix}/test-05-multiple_commits
  echo "# commit 1 of 2" >> multible_commit.md && \
        git add . && git commit -m "commit 1" && \
        echo "# commit 2 of 2" >> multible_commit.md && \
        git add . && git commit -m "commit 2"

  text="test-06-merge-empty-commit" && \
        resetToInit && checkoutMyBranch ready${branch_prefix}/$text && \
        echo "$text" && \
        git commit --allow-empty -m "$text"

  text="test-07-change-Jenkinsfile" && \
        resetToInit && checkoutMyBranch ready${branch_prefix}/$text && \
        echo "println \"$text\"" >> JenkinsfileScripted && \
        echo "println \"$text\"" >> JenkinsfileDeclarative && \
        git add . && \
        git commit -m "$text"
done
checkoutMyBranch "master" && resetToInit

git push origin --mirror

git log --graph --decorate --all --oneline
