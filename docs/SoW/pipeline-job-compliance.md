# Pipeline job compliance - statement of work

## Problem
The easiest way to implement the automation in Jenkins for the [Git Phlow](https://github.com/Praqma/git-phlow ) is to use the Pretested Integration Plugin (PIP), but it can not be used in [Pipeline jobs](https://jenkins.io/doc/book/pipeline/) described with neither [Declarative Pipeline or Scripted Pipeline syntax](https://jenkins.io/doc/book/pipeline/syntax/). It only works in the other popular alternative for scripted jobs, namely Jenkins Job DSL.
In addition the plugin restricts it use to only the Freestyle job type in Jenkins, not allowing for more creative job setups and the Matrix job types because the pretesting part (get changes, merge and run build step) is not possible to separate from the integration step where we push changes after a successful build

## Solution
We will implement support for using PIP in Pipeline jobs, both supporting Declarative Pipeline and Scripted Pipeline syntax.
The plugin will still support Jenkins Job DSL as well.
We will support separation of the pretesting part from the integration part, so more creative job construction can be used allowing the user to control these steps on their own.
Officially we will still only support Freestyle jobs.

## Implementation
To support the solution the plugin will be changed to a Git Plugin extension instead. The integration part where we push the changes will be available as an independent post-build step in Jenkins.
The plugin will _not_ be backwards compatible.

## Deliveries
New public release with support for both Pipeline job syntax and Jenkins job DSL syntax. Updated documentation, and examples of all three scripted job types.
An example of how to use it with Matrix job type, or explanation of outstanding issues for this to work and possible work-arounds.

## Work load
The work is estimated to about 60 hours of work.
