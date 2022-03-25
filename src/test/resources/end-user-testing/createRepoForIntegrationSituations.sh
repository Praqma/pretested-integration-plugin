#!/usr/bin/env bash
set -x
set -e

rm -rf work/jobs/*
#url=`ipconfig | grep IPv4 | head -1 | awk -F ": " '{print $2}' || echo localhost`
url=localhost
curl -X POST ${url}:8080/jenkins/reload || {
    sleep 60
    curl -X POST ${url}:8080/jenkins/reload || {
        sleep 20
        curl -X POST ${url}:8080/jenkins/reload
    }
}

# getting Maven build job
curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/main/maven-plugin/2.16/maven-plugin-2.16.hpi --output work/plugins/maven-plugin.hpi

# Getting MultiJob
rm -rf work/plugins/jenkins-multijob-plugin && \
    curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/jenkins-multijob-plugin/1.31/jenkins-multijob-plugin-1.31.hpi --output work/plugins/jenkins-multijob-plugin.hpi

curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/built-on-column/1.1/built-on-column-1.1.hpi -o work/plugins/built-on-column.hpi

rm -rf work/plugins/conditional-buildstep.hpi work/plugins/conditional-buildstep && \
    curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/conditional-buildstep/1.3.3/conditional-buildstep-1.3.3.hpi -o work/plugins/conditional-buildstep.jpi

curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/envinject/1.90/envinject-1.90.hpi -o work/plugins/envinject.hpi

rm -rf  work/plugins/run-condition  work/plugins/run-condition.* && \
    curl -L http://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/run-condition/1.0/run-condition-1.0.hpi -o work/plugins/run-condition.hpi
ls -la work/plugins/



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
cp `pwd`/../`dirname $0`/JenkinsfileDeclCheckoutSCM .
cp `pwd`/../`dirname $0`/JenkinsfileDeclPreSCM .
cp `pwd`/../`dirname $0`/JenkinsfileScriptedCheckoutSCM .
cp `pwd`/../`dirname $0`/JenkinsfileScriptedPreSCM .
cp `pwd`/../`dirname $0`/JenkinsfileDeclFFOnlyCheckoutSCM .

git add .
git commit -m "init"
git tag -a -m "init" init
git push origin --mirror

branch_prefixes=""
branch_prefixes="${branch_prefixes} FsExtSq"
branch_prefixes="${branch_prefixes} FsExtAcc"
branch_prefixes="${branch_prefixes} FsExtFFOnly"
branch_prefixes="${branch_prefixes} MxExtAcc MxExtSq"
#branch_prefixes="${branch_prefixes} MuExtAcc MuExtSq" // TODO: Not supported - plugin not installed
#branch_prefixes="${branch_prefixes} MvnExtAcc" // TODO: Not supported hence the plugin is not installed
branch_prefixes="${branch_prefixes} PipeDeclCheckoutSCM"
branch_prefixes="${branch_prefixes} PipeScriptedCheckoutSCM"
branch_prefixes="${branch_prefixes} PipeDeclFFOnlyCheckoutSCM"
#branch_prefixes="${branch_prefixes} PipeDeclPreSCM PipeScriptedPreSCM" // TODO: MultiBranchPipePreSCM - Not Supported Fix
#branch_prefixes="${branch_prefixes} PipeDeclScript"
#branch_prefixes="${branch_prefixes} MultiBranchPipePreSCM" // TODO: MultiBranchPipePreSCM - Not Supported Fix

# FORCE A SINGLE/FEW
#branch_prefixes="PipeDeclFFOnlyCheckoutSCM"

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

function createSimpleCommit(){
    local text=$1
    local file=$2
    echo $text >> $file
    git add $file
    git commit -m "$text"
}

function publishAndBuild(){
    git push origin $1:refs/heads/ready${branch_prefix}/${3}
#    curl -X POST http://${url}:8080/jenkins/job/test-${2}/build
if [[ ${use_direct_build_call:-} == true ]] ; then
    curl -X POST ${url}:8080/jenkins/job/test-${2}/build \
                 --data-urlencode \
                 json='\
                    {\
                        "parameter": [\
                            { \
                                "name":"git_branch_pattern", \
                                "value":"refs/heads/ready${branch_prefix}/${3}" \
                            } \
                        ] \
                    }'

fi
}

function nextTest(){

    sleep_init=60
    sleep=10
    queue="x"
    echo "sleep initially: $sleep_init"
    sleep $sleep_init
    while [[ ${queue} != "" ]] ; do
        echo "sleep: $sleep"
        sleep $sleep
        queue=$(curl --silent http://localhost:8080/jenkins/queue/api/json?pretty=true | jq .items[] )
        echo $queue
    done

#    read -n 1 -p "Enter to continue" enter
#   sleep 150
#    sleep 30
#echo
}


for branch_prefix in ${branch_prefixes} ; do
    resetToInit && checkoutMyBranch master${branch_prefix}
    git push origin master${branch_prefix}:refs/heads/master${branch_prefix}
    # Test the triggering of "no workspace"
#    curl -X POST http://${url}:8080/jenkins/job/test-${branch_prefix}/build
done

checkoutMyBranch "master" && resetToInit

for branch_prefix in ${branch_prefixes} ; do
    # Place it on to top of
    text="test-01-change-Jenkinsfile_README.dk-ff"
    resetToInit && checkoutMyBranch ready${branch_prefix}/$text

    jenkinsfile="Jenkinsfile"
    echo "println \"$text\"" > $jenkinsfile
    cat `pwd`/../`dirname $0`/$jenkinsfile >> $jenkinsfile
    git add $jenkinsfile

    jenkinsfile="JenkinsfileDeclCheckoutSCM"
    echo "println \"$text\"" > $jenkinsfile
    cat `pwd`/../`dirname $0`/$jenkinsfile >> $jenkinsfile
    git add $jenkinsfile

    jenkinsfile="JenkinsfileDeclPreSCM"
    echo "println \"$text\"" > $jenkinsfile
    cat `pwd`/../`dirname $0`/$jenkinsfile >> $jenkinsfile
    git add $jenkinsfile

    jenkinsfile="JenkinsfileScriptedCheckoutSCM"
    echo "println \"$text\"" > $jenkinsfile
    cat `pwd`/../`dirname $0`/$jenkinsfile >> $jenkinsfile
    git add $jenkinsfile

    jenkinsfile="JenkinsfileScriptedPreSCM"
    echo "println \"$text\"" > $jenkinsfile
    cat `pwd`/../`dirname $0`/$jenkinsfile >> $jenkinsfile
    git add $jenkinsfile

    echo $text >> README.md
    git add README.md
    git commit -m "$text"
    publishAndBuild HEAD ${branch_prefix} ${text}
    git tag -m "${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff" ${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff
    git push origin ${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff:refs/tags/${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff
done

for branch_prefix in ${branch_prefixes} ; do
    cp -rf `pwd`/../`dirname $0`/jobs/test-${branch_prefix} `pwd`/../work/jobs/
done
curl -X POST ${url}:8080/jenkins/reload || {
    sleep 60
    curl -X POST ${url}:8080/jenkins/reload || {
        sleep 20
        curl -X POST ${url}:8080/jenkins/reload
    }
}

#read -n 1 -p "Master and first ready branches created"  enter

#for branch_prefix in ${branch_prefixes} ; do
#  git branch -D ready${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff
#done

nextTest

for branch_prefix in ${branch_prefixes} ; do
    checkoutMyBranch ready${branch_prefix}/$text
    git reset --hard ${branch_prefix}/test-01-change-Jenkinsfile_README.dk-ff
    createSimpleCommit "test-01.1-multicommitsFF" README.md
    createSimpleCommit "test-01.2-multicommitsFF" README.md
    publishAndBuild HEAD ${branch_prefix} test-01.X-multicommitsFF
done

for branch_prefix in ${branch_prefixes} ; do
    createSimpleTestScenario "test-02-merge-conflicts" README.md
    publishAndBuild HEAD ${branch_prefix} test-02-merge-conflicts
done
nextTest

for branch_prefix in ${branch_prefixes} ; do
    text="test-03-merge-ok"
    createSimpleTestScenario "${text}" test.md
    publishAndBuild HEAD ${branch_prefix} ${text}
done
nextTest

#for branch_prefix in ${branch_prefixes} ; do
#    text="test-04-build-failed"
#    createSimpleTestScenario "${text}" build_failed.md
#    git push origin HEAD:refs/heads/ready${branch_prefix}/${text}
#done
#nextTest

for branch_prefix in ${branch_prefixes} ; do
    text="test-05-multiple_commits"
    resetToInit && checkoutMyBranch ready${branch_prefix}/${text}
    echo "# commit 1 of 2" >> multible_commit.md
    git add . && git commit -m "commit 1"
    git tag ${branch_prefix}_NOT_BUILT
    echo "# commit 2 of 2" >> multible_commit.md
    git add . && git commit -m "commit 2"
    publishAndBuild HEAD ${branch_prefix} ${text}
done
nextTest

for branch_prefix in ${branch_prefixes} ; do
#    git push origin refs/tags/${branch_prefix}_NOT_BUILT:refs/heads/ready${branch_prefix}/test-05.1-NOT_BUILT
    publishAndBuild refs/tags/${branch_prefix}_NOT_BUILT ${branch_prefix} test-05.1-NOT_BUILT
done
nextTest

for branch_prefix in ${branch_prefixes} ; do
    text="test-06-merge-empty-commit"
    resetToInit && checkoutMyBranch ready${branch_prefix}/$text
    echo "$text"
    git commit --allow-empty -m "$text"
    publishAndBuild HEAD ${branch_prefix} ${text}
done
nextTest

for branch_prefix in ${branch_prefixes} ; do
    text="test-07-change-Jenkinsfile"
    resetToInit && checkoutMyBranch ready${branch_prefix}/$text
    echo "println \"$text\"" >> Jenkinsfile
    echo "println \"$text\"" >> JenkinsfileDeclCheckoutSCM
    echo "println \"$text\"" >> JenkinsfileDeclPreSCM
    echo "println \"$text\"" >> JenkinsfileScriptedCheckoutSCM
    echo "println \"$text\"" >> JenkinsfileScriptedPreSCM
    git add . && git commit -m "$text"
    publishAndBuild HEAD ${branch_prefix} ${text}
done

checkoutMyBranch "master" && resetToInit

git fetch origin -ap -f
branches=$(git branch -r | wc -l)
branches_changed=true
while $branches_changed == true ; do
    branches_before=$branches
    sleep_init=60
    sleep=10
    busy_executors="x"
    echo "sleep initially: $sleep_init"
    sleep $sleep_init
    while [[ ${busy_executors} != "" ]] ; do
        echo "sleep: $sleep"
        sleep $sleep
        busy_executors=$(curl --silent http://localhost:8080/jenkins/overallLoad/api/json?pretty=true | jq  .busyExecutors[] )
        echo $busy_executors
    done
    git fetch origin -ap -f || {
        sleep 60
        git fetch origin -ap -f
    }

  branches=$(git branch -r | wc -l)
  [[ $branches -eq $branches_before ]] && branches_changed=false
done

git log --graph --oneline --all > git_graph.txt

test_exit_code=0
set +x
for branch_prefix in ${branch_prefixes} ; do
  git branch -r --list *${branch_prefix}*
  if [[ -s $(pwd)/../$(dirname $0)/test_references/git_log_${branch_prefix}.log ]]; then
     references=$( git branch -r --list *${branch_prefix}* )
     git log --graph --oneline --format="%s" $references > $(pwd)/git_log_${branch_prefix}.log
     dos2unix git_log_${branch_prefix}.log
     if diff -y $(pwd)/git_log_${branch_prefix}.log $(pwd)/../$(dirname $0)/test_references/git_log_${branch_prefix}.log > $(pwd)/git_log_${branch_prefix}_diff.log; then
        echo "TEST ok: $branch_prefix"
        echo "$(pwd)/git_log_${branch_prefix}.log $(pwd)/../$(dirname $0)/test_references/git_log_${branch_prefix}.log > $(pwd)/git_log_${branch_prefix}_diff.log"
     else
        echo "TEST failed: $branch_prefix"
        echo "$(pwd)/git_log_${branch_prefix}.log $(pwd)/../$(dirname $0)/test_references/git_log_${branch_prefix}.log > $(pwd)/git_log_${branch_prefix}_diff.log"
        [[ ${test_exit_code} -eq 0 ]] && test_exit_code=1
     fi
  else
    echo "TEST skip: no reference file: $(pwd)/../$(dirname $0)/test_references/git_log_${branch_prefix}.log"
  fi
done
set -x
if [[ ${test_exit_code} -eq 0 ]]; then
 echo "All tests went well .."
else
 echo "Some tests failed.. "
 exit $test_exit_code
fi





