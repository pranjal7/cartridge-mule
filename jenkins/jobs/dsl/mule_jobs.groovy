// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def muleEnvRepoName = "mule"
def muleGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + muleEnvRepoName


// Jobs
def buildJob = freeStyleJob(projectFolderName + "/afp4Mule-Build")
def sonarJob = freeStyleJob(projectFolderName + "/afp4Mule-Sonar")
def packageJob = freeStyleJob(projectFolderName + "/afp4Mule-Package")
def deployJob = freeStyleJob(projectFolderName + "/afp4Mule-Deploy")

// Views
def MuleBuildPipelineView = buildPipelineView(projectFolderName + "/Mule_Deployment")
MuleBuildPipelineView.with{
    title('Mule Deployment Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/afp4Mule-Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

buildJob.with{
    description("The AFP4Mule reference application build job.")
    logRotator {
    daysToKeep(7)
    numToKeep(7)
	artifactDaysToKeep(7)
	artifactNumToKeep(7)
    }	
    scm {
        git {
            remote {
                url("${muleGitUrl}")
                credentials("adop-jenkins-master")
            }
			branch("*/develop")
		}
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    triggers {
        gerrit{
            events{
                refUpdated()
            }
            configure { gerritxml ->
                gerritxml / 'gerritProjects' {
                    'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
                        compareType("PLAIN")
                        pattern(projectFolderName + "/" + muleEnvRepoName)
                        'branches' {
                            'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
                                compareType("PLAIN")
                                pattern("develop")
                            }
                        }
                    }
                }
                gerritxml / serverName("__ANY__")
            }
        }
    }
	concurrentBuild(false)
    steps {	
		maven {
			mavenInstallation("ADOP Maven")
			goals("clean install -U -DskipTests")
			rootPOM("mule-services-usa/pom.xml")
		}
		systemGroovyScriptFile("/var/jenkins_home/scriptler/scripts/pipeline_params.groovy") {
        }
    publishers{
		archiveArtifacts {
            pattern('**/*')
			allowEmpty(false)
            onlyIfSuccessful(false)
			fingerprint(true)
			defaultExcludes(true)	
        }
		git {
			pushMerge(false)
            pushOnlyIfSuccess(true)
			forcePush(true)
            tag('origin', '$JOB_NAME-$BUILD_NUMBER') {
                message()
                create(true)
            }
        downstreamParameterized{
            trigger(projectFolderName + "/afp4Mule-Sonar"){
            condition("SUCCESS")
            parameters{
                predefinedProp("B",'${BUILD_NUMBER}')
                predefinedProp("PARENT_BUILD",'${JOB_NAME}')               
                }
            }
			triggerWithNoParameters(false)
        } 
    }
}

sonarJob.with{
    description("Sample AFP4Mule Sonar static code analysis job")
    logRotator {
    daysToKeep(7)
    numToKeep(7)
	artifactDaysToKeep(7)
	artifactNumToKeep(7)
    }	
    parameters{
        stringParam("B",,"The build number of the parent build.")
        stringParam("PARENT_BUILD",,"The parent build to pull artifacts from.")
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        copyArtifacts('${PARENT_BUILD}') {
			buildSelector {
                buildNumber('${B}')
            }
			fingerprintArtifacts(true)
    }
    publishers{
		sonar {
			mavenInstallation(ADOP Maven)
			rootPOM("mule-services-usa/pom.xml")
			additionalProperties(-Dsonar.scm.url=scm:git:https://innersource.accenture.com/digital-1/afp4mule-reference-app.git)
            branch('feature-xy')
            overrideTriggers {
                skipIfEnvironmentVariable('SKIP_SONAR')
            }		
        downstreamParameterized{
            trigger(projectFolderName + "/afp4Mule-Sonar"){
            condition("SUCCESS")
            parameters{
                predefinedProp("B",'${BUILD_NUMBER}')
                predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')               
                }
            }
			triggerWithNoParameters(false)
        } 
    }
}

packageJob.with{
}

deployJob.with{
}
