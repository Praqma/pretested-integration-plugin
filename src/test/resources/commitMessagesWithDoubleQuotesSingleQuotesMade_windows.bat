@echo on

SET NAME=commitMessagesWithDoubleQuotesSingleQuotesMade_windows
SET REPO_NAME=commitMessagesWithDoubleQuotesSingleQuotesMade_windows
SET WORK_DIR=%~dp0


REM pre-process

mkdir %REPO_NAME%.git
cd %REPO_NAME%.git
git init --bare

cd %WORK_DIR%
git clone %REPO_NAME%.git
cd %REPO_NAME%
git config user.name "Praqma Support"
git config user.email "support@praqma.net"


touch README.md
echo "# README of repository $REPO_NAME" >> README.md
echo "" >> README.md
echo "This is a test repository for functional tests." >> README.md
git add README.md
git commit -m "Initial commit - added README"


git push origin master

REM custom parts

SET BN=JENKINS-27662_doublequotes
git checkout -b dev/%BN%
touch testCommit.log
echo "# Test commit log" >> testCommit.log
echo "" >> testCommit.log
echo "Used for adding lines to commit something during tests.\n" >> testCommit.log
git add testCommit.log
git commit -m "Added test commit log file"

REM Problematic commit message with double quotes

echo "Added a new line to this file, to commit something. Commit message will have double quotes" >> testCommit.log
git add testCommit.log
git commit -m 'This is a commit message with double quotes (commit made on Windows), and =, eg. "test quotes".'
git push origin dev/%BN%

REM also push to ready branch, so integration can start during the test

git push origin dev/%BN%:ready/%BN%
git remote -v show

REM Post process

cd %WORK_DIR%
7z a -r %NAME%%VERSION%.zip %REPO_NAME%.git

rmdir /S /Q %REPO_NAME%.git
del /Q %REPO_NAME%
rmdir /S /Q %REPO_NAME%
