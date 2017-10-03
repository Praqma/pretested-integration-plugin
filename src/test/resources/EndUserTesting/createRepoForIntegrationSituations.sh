#!/usr/bin/env bash
set -x

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
cp `pwd`/../`dirname $0`/Jenkinsfile .
git add .
git commit -m "init"
git tag -a -m "init" init

branch_prefixes="FsExtSq FsExtAcc FsBwAcc FsBwSq FsExtBwAcc MxExtAcc MxExtSq MuExtAcc MuExtSq PipeSCM PipeScript"
#branch_prefixes="PipeSCM PipeScript"
# TODO. Add test for pushing to integrationBranch

function resetToInit(){
    git checkout master
    git reset --hard init
    git clean -xfd
}

function checkoutMyBranch(){
    git checkout -B $1 refs/tags/init
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
    createSimpleTestScenario "test-01-ff" README.md
done
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
        echo "println \"$text\"" >> Jenkinsfile && \
        git add . && \
        git commit -m "$text"
done
resetToInit && checkoutMyBranch master

git push origin --mirror

git log --graph --decorate --all --oneline


