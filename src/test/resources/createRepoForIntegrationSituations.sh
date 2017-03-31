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

sleep_sec=1
git checkout -B master
echo "# HEADER" >> README.md
git add .
git commit -m "init"
git tag -a -m "init" init
git push origin -f --mirror
git push origin -f master:refs/heads/masterFS
git push origin -f master:refs/heads/masterMatrix
git push origin -f master:refs/heads/masterMultijob
git push origin -f master:refs/heads/masterBW
git push origin -f master:refs/heads/masterBWSquash


#branch_prefixes="FS BW BWSquash Matrix Multijob"
branch_prefixes="Matrix"
# TODO. Add test for pushing to integrationBranch

for branch_prefix in ${branch_prefixes} ; do
  sleep ${sleep_sec}
  echo "test new branch but same sha1 as master" && \
        git reset --hard origin/master${branch_prefix} && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-00-sha1-as-master && \
        git reset --hard init
  echo "test FF" >> README.md && \
        git add . && git commit -m "test FF" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-01-ff && \
        git reset --hard init
  echo "test merge/squash failure (Conflict)" >> README.md && \
        git add . && git commit -m "test conflicts" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-02-merge-conflicts && \
        git reset --hard init
  echo "test merge/squash ok" >> test.md && \
        git add . && git commit -m "test merge ok" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-03-merge-ok && \
        git reset --hard init
  echo "# build-failed" >> build_failed.md && \
        git add . && git commit -m "build failed" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-04-build-failed && \
        git reset --hard init
  echo "# commit 1 of 2" >> multible_commit.md && \
        git add . && git commit -m "commit 1" && \
        echo "# commit 2 of 2" >> multible_commit.md && \
        git add . && git commit -m "commit 2" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-05-multiple_commits && \
        git reset --hard init
  echo "test empty commit merge/rebase/squash failure" && \
        git commit --allow-empty -m "test empty commit merge failure" && \
        git push origin HEAD:refs/heads/ready${branch_prefix}/test-06-merge-empty-commit-failure && \
        git reset --hard init
done
