#!/bin/bash

#If two branch heads points to same commit, the build data from git have two branches for the same commit.
#
#The "wrong" branch may be integrated and deleted.
#
#Prested uses the first branch given from the git plugin, which seems to be the last branch name added for the commit.
#
#
#The following workflow shows it a given repository, and a jenkins job configured to use pretested integration with branch specifier "origin/ready/**" and destination "master".
#
#
#git checkout master
#git fetch --prune
#
#git checkout -b my-dev
#echo "text" > text.text
#git add text.text
#git commit -m "A test file"
#
#git checkout master
#git push origin my-dev:ready/my-dev
#git push origin my-dev:ready/alpha-dev
#
#---
#
#The above will integrate and delete alpha-dev, and vice verse if the last to push were in reverse order.
#
#Both branches respect the branch specifier, so both need to be integrated, so we need to make a decision which one is the correct branch?
#Further - what to do with the other branch?
#
#https://trello.com/c/MFzaEMDz

RB="
alpha-dev
ready/alpha-dev
ready/my-dev
team-dev
"
NO="6"
OLD="5"

echo "**********"
git fetch --prune
git checkout master
git pull origin master
echo "**********"

git branch -a

git branch -D my-dev
echo "**********"

for b in $RB 
do
	git push origin :$b-$NO
	git push origin :$b-$OLD
done

echo "**********"

git checkout -b my-dev-$NO

echo "NANO SECS COMMIT" >> text.txt
date +%N >> text.text
git add text.txt 
git commit -m "NANO SECS COMMIT"
git branch -a
echo "**********"

LAST_SHA=`git rev-parse HEAD`

echo "contain commit"
git branch --contains $LAST_SHA
git branch --contains $LAST_SHA

echo "**********"

git checkout master

git push origin my-dev-$NO:alpha-dev-$NO
git push origin my-dev-$NO:ready/my-dev-$NO
git push origin my-dev-$NO:ready/alpha-dev-$NO
git push origin my-dev-$NO:team-dev-$NO

echo "**********"

