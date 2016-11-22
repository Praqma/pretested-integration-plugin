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

sleep_sec=10
git checkout -B master
echo "# HEADER" >> README.md
git add .
git commit -m "init"
git tag -a -m "init" init
git push origin -f --mirror
git push origin -f master:refs/heads/masterMatrix
git push origin -f master:refs/heads/masterMultijob

branch_prefix="ready-bw" && git reset --hard init && echo "test FF" >> README.md && git add . && git commit -m "test FF" && git push origin HEAD:refs/heads/${branch_prefix}/test-01-ff && git reset --hard init
branch_prefix="ready-bw" && git reset --hard init && echo "test merge failure" >> README.md && git add . && git commit -m "test merge failure" && git push origin HEAD:refs/heads/${branch_prefix}/test-02-merge-failure && git reset --hard init
branch_prefix="ready-bw" && git reset --hard init && echo "test merge ok" >> test.md && git add . && git commit -m "test merge ok" && git push origin HEAD:refs/heads/${branch_prefix}/test-03-merge-ok && git reset --hard init
branch_prefix="ready-bw" && git reset --hard init && echo "# build-failed" >> build_failed.md && git add . && git commit -m "build failed" && git push origin HEAD:refs/heads/${branch_prefix}/test-04-build-failed && git reset --hard init
branch_prefix="ready-bw" && git reset --hard init \
&& echo "# commit 1" >> multible_commit.md && git add . && git commit -m "commit 1" \
&& echo "# commit 2" >> multible_commit.md && git add . && git commit -m "commit 2" \
&& git push origin HEAD:refs/heads/${branch_prefix}/test-05-multiple_commits && git reset --hard init

if [ "X" == "Y" ]; then
branch_prefix="ready" && git reset --hard init && echo "test FF" >> README.md && git add . && git commit -m "test FF" && git push origin HEAD:refs/heads/${branch_prefix}/test-01-ff && git reset --hard init
branch_prefix="readyMatrix" && git reset --hard init && echo "test FF" >> README.md && git add . && git commit -m "test FF" && git push origin HEAD:refs/heads/${branch_prefix}/test-01-ff && git reset --hard init
branch_prefix="readyMultijob" && git reset --hard init && echo "test FF" >> README.md && git add . && git commit -m "test FF" && git push origin HEAD:refs/heads/${branch_prefix}/test-01-ff && git reset --hard init
sleep ${sleep_sec}
branch_prefix="ready" && git reset --hard init && echo "test merge failure" >> README.md && git add . && git commit -m "test merge failure" && git push origin HEAD:refs/heads/${branch_prefix}/test-02-merge-failure && git reset --hard init
branch_prefix="readyMatrix" && git reset --hard init && echo "test merge failure" >> README.md && git add . && git commit -m "test merge failure" && git push origin HEAD:refs/heads/${branch_prefix}/test-02-merge-failure && git reset --hard init
branch_prefix="readyMultijob" && git reset --hard init && echo "test merge failure" >> README.md && git add . && git commit -m "test merge failure" && git push origin HEAD:refs/heads/${branch_prefix}/test-02-merge-failure && git reset --hard init
sleep ${sleep_sec}
branch_prefix="ready" && git reset --hard init && echo "test merge ok" >> test.md && git add . && git commit -m "test merge ok" && git push origin HEAD:refs/heads/${branch_prefix}/test-03-merge-ok && git reset --hard init
branch_prefix="readyMatrix" && git reset --hard init && echo "test merge ok" >> test.md && git add . && git commit -m "test merge ok" && git push origin HEAD:refs/heads/${branch_prefix}/test-03-merge-ok && git reset --hard init
branch_prefix="readyMultijob" && git reset --hard init && echo "test merge ok" >> test.md && git add . && git commit -m "test merge ok" && git push origin HEAD:refs/heads/${branch_prefix}/test-03-merge-ok && git reset --hard init
sleep ${sleep_sec}
branch_prefix="ready" && git reset --hard init && echo "# build-failed" >> build_failed.md && git add . && git commit -m "build failed" && git push origin HEAD:refs/heads/${branch_prefix}/test-04-build-failed && git reset --hard init
branch_prefix="readyMatrix" && git reset --hard init && echo "# build-failed" >> build_failed.md && git add . && git commit -m "build failed" && git push origin HEAD:refs/heads/${branch_prefix}/test-04-build-failed && git reset --hard init
branch_prefix="readyMultijob" && git reset --hard init && echo "# build-failed" >> build_failed.md && git add . && git commit -m "build failed" && git push origin HEAD:refs/heads/${branch_prefix}/test-04-build-failed && git reset --hard init
sleep ${sleep_sec}


branch_prefix="ready" && git reset --hard init \
&& echo "# commit 1" >> multible_commit.md && git add . && git commit -m "commit 1" \
&& echo "# commit 2" >> multible_commit.md && git add . && git commit -m "commit 2" \
&& git push origin HEAD:refs/heads/${branch_prefix}/test-05-multiple_commits && git reset --hard init


branch_prefix="readyMatrix" && git reset --hard init \
&& echo "# commit 1" >> multible_commit.md && git add . && git commit -m "commit 1" \
&& echo "# commit 2" >> multible_commit.md && git add . && git commit -m "commit 2" \
&& git push origin HEAD:refs/heads/${branch_prefix}/test-05-multiple_commits && git reset --hard init
branch_prefix="readyMultijob" && git reset --hard init \
&& echo "# commit 1" >> multible_commit.md && git add . && git commit -m "commit 1" \
&& echo "# commit 2" >> multible_commit.md && git add . && git commit -m "commit 2" \
&& git push origin HEAD:refs/heads/${branch_prefix}/test-05-multiple_commits && git reset --hard init

fi