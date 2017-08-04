# End users testing with the build-in Jenkins instance
A set of jobs has been created in order to easy make end user testing. Currently the jobs are hardcoded copy xml, but 
should be chagned to JobDSL etc and later also Pipeline with the support is there

## HowTo run the EndUser test scenarios
 * Create the test repo
   * Run the script `src/test/resources/createRepoForIntegrationSituations.sh`
   * TODO: parameterize the script to take a `repo-url`
 * Setup the jobs in Jenkins
   * cp the jobs from `jobs` to `<root>/works/jobs`
   * Install these plugins manually:
     * `text-finder`
     * `conditional-buildstep`
   * Run the Jenkins instance (hpi:run) in Run or Debug mode
   
## How to easy clean and rerun (A)
 * Delete the jenkins jobs produced contents
   * Stop the Jenkins instance
   * run `for job in $(ls -1d work/jobs/test*) ; do rm -rf $job/builds $job/workspace $job/configurations $job/nextBuildNumber $job/scm-polling.log; done`
   * `cd test-git-phlow-plugin && git push origin --mirror -f && cd -`
   * Start the Jenkins Instance

## How to easy clean and rerun (B)
 * run `for job in $(ls -1d work/jobs/test*) ; do rm -rf $job/builds $job/workspace $job/configurations $job/nextBuildNumber $job/scm-polling.log; done`
 * Open url `localhost:8080/jenkins/reload` -> hit the button
 * `cd test-git-phlow-plugin && git push origin --mirror -f && cd -`
