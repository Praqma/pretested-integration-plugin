# Branch Context for PIP - statement of Work

## Problem

When an integration is not successful the only place to find information is in the relevant Jenkins build. If the failures are not noticed and handled immediately you can easily loose the overview of the state of all the ready branches when you only look at the branches in your DVCS. Are the ready branches there because they failed, or are they waiting in queue? To see the status you would have to visit the Jenkins server, but it might have been easier to detect the problem in context of some branch naming conventions.

## Solution
We propose a solution where we supply information about the integration process as a branch context by renaming the ready branches processed.
The context is applied as follows to a ready-branch pushed to `ready/my-feature`
* When processing it is renamed `wip/my-feature`
* When successfully integrated the branch is deleted
* If integration fails due og merge failure it is left renamed as `merge-failed/my-feature`
* If build fails (build steps, or any other reason) the branch is left renamed as `build-failed/my-feature`

When the problem is solved, we support that the developer just pushes to the same ready-branch (`ready/my-feature`).
The plugin cleans up automatically all branches used in the workflow, meaning if success all ``*/my-feature` is deleted.

**Renamed means the original ready/my-feature branch is deleted.**

**Branch context support layers of branch prefixes, so it will also work with ready-branches called `rel-1.0.0/ready/my-feature`.**
For example a merge failure end in `rel-1.0.0/merged-failed/my-feature`, and when again successful all branches matchin `rel-1.0.0/*/my-feature` is cleaned.

## Implementation
This new branch context feature will be backwards compatible, and will be available as a configuration choice. We will not include support to configure the renaming patterns, but we can decide the naming patterns together before implementation.

## Deliveries
New public release with branch context support and updated documentation for this feature.

## Work load
The workload for this piece of work is estimated to about 40 hours.
