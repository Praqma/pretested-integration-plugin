rm -rf mytest mytest2 mytest3 mytest4 

BDIR=`pwd`

# First create tw repos, and shows that branches are listed in what seems to be alpha-numeric order:

git init mytest
cd mytest/
date +%N> date.log
git add date.log 
git commit -m "date  log 1"
git checkout -b ready/bue-dev
date +%N>> date.log
git add date.log 
git commit -m "date  log 2 lines"
date +%N>> date.log
git add date.log 
git commit -m "date  log 3 lines"
git checkout -b team-frontend/dev
git log
LAST_SHA=`git rev-parse HEAD`
git branch --contains $LAST_SHA
git checkout ready/bue-dev
git branch --contains $LAST_SHA
git checkout master
git branch --contains $LAST_SHA

git checkout master
git merge --squash ready/bue-dev
git commit -C ready/bue-dev


cd $BDIR
git init mytest2
cd mytest2
date +%N> date.log
git add date.log 
git commit -m "date  log 1"
git checkout -b feature/team-dev
date +%N>> date.log
git add date.log 
git commit -m "date  log 2 lines"
date +%N>> date.log
git add date.log 
git commit -m "date  log 3 lines"
git checkout -b ready/bue-dev
git log 
LAST_SHA=`git rev-parse HEAD`
git branch --contains $LAST_SHA
git checkout feature/team-dev
git branch --contains $LAST_SHA
git checkout master
git branch --contains $LAST_SHA

git checkout master
git merge --squash ready/bue-dev
git commit -C ready/bue-dev


cd $BDIR

echo -------------------------

git init mytest3
cd mytest3/
date +%N> date.log
git add date.log 
git commit -m "date  log 1"
git checkout -b ready/bue-dev
date +%N>> date.log
git add date.log 
git commit -m "date  log 2 lines"
date +%N>> date.log
git add date.log 
git commit -m "date  log 3 lines"
git checkout -b team-frontend/dev
git log
LAST_SHA=`git rev-parse HEAD`
git branch --contains $LAST_SHA
git checkout ready/bue-dev
git branch --contains $LAST_SHA
git checkout master
git branch --contains $LAST_SHA

git checkout master
git merge -m "accumulated commit merge" ready/bue-dev --no-ff



cd $BDIR

git init mytest4
cd mytest4
date +%N> date.log
git add date.log 
git commit -m "date  log 1"
git checkout -b feature/team-dev
date +%N>> date.log
git add date.log 
git commit -m "date  log 2 lines"
date +%N>> date.log
git add date.log 
git commit -m "date  log 3 lines"
git checkout -b ready/bue-dev
git log 
LAST_SHA=`git rev-parse HEAD`
git branch --contains $LAST_SHA
git checkout feature/team-dev
git branch --contains $LAST_SHA
git checkout master
git branch --contains $LAST_SHA

git checkout master
git merge -m "accumulated commit merge" ready/bue-dev --no-ff
