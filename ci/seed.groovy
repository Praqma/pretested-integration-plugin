/*
    1. Mask passwords plugin must be isntalled.
    2. Two environment variables must be available for the release and beta release jobs
       A) PWFORPRAQMAMVNUSER - The password for your jenkins-ci.org account (Artifactory)
       B) PW_HTTPS_RELEASE_PRAQMA - Https password for your GitHub user account
 */

def pretestedGitUrl = "https://github.com/Praqma/pretested-integration-plugin.git"
def credentialsId = "github"
def pipJobName = "pretested-integration-plugin-verify"
def devBranchPattern = "master"
def integrationBranchName = "master"

def descriptionForPipJob = """<h3>Pretested Integration Plugin</h3>
<p>Requires a volume pretested-integration-plugin-volume which is mounted to the .m2 directory of the docker image</p>
<p>This is done to avoid having to download dependencies every time</p> 
"""
job(pipJobName) {
    description("Runs pretested integration for Pretested Integration ")
    scm {
        git {
            branch("\${BRANCH}") //TODO: Switch to ready when done
            remote {
                name("origin")
                url(pretestedGitUrl)
                credentials(credentialsId)
            }
        }
    }

    steps {
        shell("docker run -w /usr/src/mymaven --rm -t -v \$(pwd):/usr/src/mymaven -v pretested-integration-plugin-volume:/root/.m2 maven:3.5.2-jdk-8 /bin/bash -c 'git config --global user.email \"release@praqma.net\" && git config --global user.name \"Praqma Release User\" && mvn -B -DdryRun=true clean release:clean release:prepare'")
    }

    publishers {
        archiveJunit('**/target/failsafe-reports/TEST*.xml')
        /* pretestedIntegrationPublisher() */
        buildPipelineTrigger('pretested-integration-plugin-release, pretested-integration-plugin-release-beta') {
            parameters {
                gitRevision()
            }
        }
    }
}

job("pretested-integration-plugin-release-beta") {
    scm {
        git {
            branch(integrationBranchName)
            remote {
                name("origin")
                url(pretestedGitUrl)
                credentials(credentialsId)
            }
        }
    }
    wrappers {
        maskPasswords()
        injectPasswords {
            injectGlobalPasswords()
            maskPasswordParameters()
        }
    }
    steps {
        //run -v ~/.m2:/var/maven/.m2 -v $(pwd):/usr/app -w /usr/app -t --rm -u 1000:1000 -e MAVEN_CONFIG=/var/maven/.m2
        //shell("docker run -w /usr/src/mymaven --rm -t -v \$(pwd):/usr/src/mymaven -v pretested-integration-plugin-volume:/root/.m2 -e PWFORPRAQMAMVNUSER=${PWFORPRAQMAMVNUSER} maven:3.5.2-jdk-8 /bin/bash -c \"git config --global user.email \\\"release@praqma.net\\\" && git config --global user.name \\\"Praqma Release User\\\" && mvn -s settings.xml -B -Dusername=ReleasePraqma -Dpassword=${PW_HTTPS_RELEASE_PRAQMA} clean release:clean release:prepare release:perform\"")
        shell("docker run -w /usr/src/app -u 1000:1000 --rm -t -v \$(pwd):/usr/src/app -v ~/.m2:/var/maven/.m2 -e PWFORPRAQMAMVNUSER=${PWFORPRAQMAMVNUSER} -e MAVEN_CONFIG=/var/maven/.m2 maven:3.5.2-jdk-8 /bin/bash -c \"git config --global user.email \\\"release@praqma.net\\\" && git config --global user.name \\\"Praqma Release User\\\" && mvn -s settings.xml -B -Dusername=ReleasePraqma -Dpassword=${PW_HTTPS_RELEASE_PRAQMA} -Duser.home=/var/maven clean release:clean release:prepare release:perform\"")
    }
}

job("pretested-integration-plugin-release") {
    scm {
        git {
            remote {
                name("origin")
                url(pretestedGitUrl)
                credentials(credentialsId)
            }
        }
    }
    steps {
        shell("echo 'Hello world! from pretested-integration-plugin-release-beta'")
    }
}

job("pretested-integration-plugin-sync") {
    triggers {
        upstream("pretested-integration-plugin-release")
    }
}


buildPipelineView("pretested-integration-plugin-build-pipeline-view") {
    title("Pretested Integration Build Pipeline View")
    selectedJob(pipJobName)
}
