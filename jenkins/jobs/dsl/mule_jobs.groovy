// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def muleEnvRepoName = "afp4mule-reference-app"
def muleGitEnvUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + muleEnvRepoName
def devOpsEnvRepoName = "devops_envs"
def devOpsEnvGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + devOpsEnvRepoName

// Jobs
def buildJob = freeStyleJob(projectFolderName + "/afp4Mule-Build")
def sonarJob = freeStyleJob(projectFolderName + "/afp4Mule-Sonar")
def packageJob = freeStyleJob(projectFolderName + "/afp4Mule-Package")
def deployJob = freeStyleJob(projectFolderName + "/afp4Mule-Deploy")

// Views
def MuleBuildPipelineView = buildPipelineView(projectFolderName + "/Mule_Code_Pipeline")
MuleBuildPipelineView.with{
    title('Mule Code Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/afp4Mule-Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

buildJob.with{
    description("The AFP4Mule application build job.")
    logRotator {
    daysToKeep(7)
    numToKeep(7)
	artifactDaysToKeep(7)
	artifactNumToKeep(7)
    }
    scm {
        git {
            remote {
                url("${muleGitEnvUrl}")
                credentials("adop-jenkins-master")
            }
			branch("*/master")
		}
    }
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
	}
    properties {
        configure { project ->
            project / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
            'string' "afp4Mule-Package"
            'string' "afp4Mule-Sonar"
            }
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
                                pattern("master")
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
			goals('clean install -U -DskipTests')
		}
		systemGroovyScriptFile('/var/jenkins_home/scriptler/scripts/pipeline_params.groovy') {
        }
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
		}
        downstreamParameterized{
            trigger(projectFolderName + "/afp4Mule-Sonar"){
            condition("SUCCESS")
			triggerWithNoParameters(false)
            parameters{
                predefinedProp("B",'${BUILD_NUMBER}')
                predefinedProp("PARENT_BUILD",'${JOB_NAME}')
                }
            }
        }
    }
}

sonarJob.with{
    description("Sample AFP4Mule Sonar static code analysis job.")
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
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
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
    }
    publishers{
		configure {
			it / 'publishers' << 'hudson.plugins.sonar.SonarPublisher' {
			jobAdditionalProperties '-Dsonar.scm.url=scm:git:ssh://gerrit:29418/D1SE_Workspace/D1SE_Project/Cartridge-Mule/afp4mule-reference-app'
			mavenInstallationName 'ADOP Maven'
			rootPom 'pom.xml'
			}
		}
        downstreamParameterized{
            trigger(projectFolderName + "/afp4Mule-Package"){
				condition("UNSTABLE_OR_BETTER")
				triggerWithNoParameters(false)
				parameters{
                predefinedProp("B",'${BUILD_NUMBER}')
                predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')
				}
			}
		}
    }
}

packageJob.with{
	description("The AFP4Mule application artifact package job.")
    logRotator {
    daysToKeep(7)
    numToKeep(7)
	artifactDaysToKeep(7)
	artifactNumToKeep(7)
    }
    parameters{
        stringParam("B",,"The build number from the PARENT_BUILD to pull artifacts.")
        stringParam("PARENT_BUILD",,"The parent build to search for packages.")
    }
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
	}
    properties {
        configure { project ->
            project / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
            'string' "afp4Mule-Deploy"
            }
        }
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
		maven {
			mavenInstallation("ADOP Maven")
			goals('deploy:deploy-file')
			goals('-DpomFile=pom.xml')
			goals('-Dversion=${B}')
			goals('-DgeneratePom=false')
			goals('-Dpackaging=zip')
			goals('-Dfile=${WORKSPACE}/target/afp4mule-reference-app.zip')
			goals('-DrepositoryId=deployment')
			goals('-Durl=http://nexus:8081/nexus/content/repositories/releases')
		}
    }
    publishers{
		archiveArtifacts {
            pattern('target/*.zip')
			pattern('afp4mule_env_default.properties')
			allowEmpty(false)
            onlyIfSuccessful(false)
			fingerprint(false)
			defaultExcludes(true)
        }
        downstreamParameterized{
            trigger(projectFolderName + "/afp4Mule-Deploy"){
            condition("UNSTABLE_OR_BETTER")
			triggerWithNoParameters(false)
				parameters{
					predefinedProp("B",'${BUILD_NUMBER}')
					predefinedProp("PARENT_BUILD",'${JOB_NAME}')
				}
            }
        }
    }
}

deployJob.with{
	description("The AFP4Mule application deploy job.")
    logRotator {
    daysToKeep(7)
    numToKeep(7)
	artifactDaysToKeep(7)
	artifactNumToKeep(7)
    }
    parameters{
        stringParam("B","","The build number of the parent build to pull.")
        stringParam("PARENT_BUILD",projectFolderName + "/afp4Mule-Build","The parent build to pull the artifact from.")
		stringParam("ENVIRONMENT","ENV001","Environment to deploy build.")
		stringParam("MULE_EE_SERVER_IP","10.0.6.6","Mule EE server ipaddress")
		stringParam("MULE_EE_CONTAINER_NAME","mule-runtime","Mule EE container name")
    }
	environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)

	}
	label('docker')
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
	scm {
        git {
            remote {
                url("${devOpsEnvGitUrl}")
                credentials("adop-jenkins-master")
				relativeTargetDir("devops_envs/")
            }
			branch("*/master")
		}
    }
    steps {
        copyArtifacts('${PARENT_BUILD}') {
			buildSelector {
                buildNumber('${B}')
            }
			includePatterns('target/*.zip')
			includePatterns('afp4mule_env_default.properties')
			fingerprintArtifacts(true)
		}
		shell('''#!/bin/bash --login

set -xe
DOCKER_VERSION=1.6.0

wget https://get.docker.com/builds/Linux/x86_64/docker-${DOCKER_VERSION} --quiet -O docker
chmod +x ./docker

yum install -y zip unzip

echo
echo "***************************************"
echo  "Preparing build artifact(s) for tokenization"
echo  "  - application of environment specific configuration."
echo "***************************************"

ARTIFACTS=$(find target/ -name \\*.zip) 

mkdir -p ${WORKSPACE}/{artifacts,tokenized}/

for ARTIFACT in ${ARTIFACTS}
do
  unzip $ARTIFACT -d ${WORKSPACE}/artifacts/$(basename $ARTIFACT .zip)
done

# Clear up target directory
rm -rf ${WORKSPACE}/target/

echo
echo "***************************************"
echo  "Tokenizing build(s)"
echo "***************************************"

./docker login -u devops.training -p ztNsaJPyrSyrPdtn -e devops.training@accenture.com docker.accenture.com
echo "Login to Docker Registry was successful"
#./docker \
	run \
    -t \
    --rm \
    -v /data/jenkins/home/jobs/afp4mule/jobs/afp4Mule-sf-deploy/workspace/:/root/workdir/artifacts/ \
    docker.accenture.com/adop/tokenization:0.0.1 perl -e 'open (MYFILELIST, ">", "/root/workdir/artifacts/files_to_tokenise.txt") || die $!;my @targetFiles=qx(find /root/workdir/artifacts/ -name "*.template");foreach (@targetFiles) {print MYFILELIST "file=$_"};close MYFILELIST;'

./docker \
	run \
    -t \
    --rm \
    -v /var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/afp4Mule-Deploy/:/root/workdir/artifacts/ \
    docker.accenture.com/adop/tokenization:0.0.1 perl -e 'open (MYFILELIST, ">", "/root/workdir/artifacts/files_to_tokenise.txt") || die $!;my @targetFiles=qx(find /root/workdir/artifacts/ -name "*.template");foreach (@targetFiles) {print MYFILELIST "file=$_"};close MYFILELIST;'

#./docker \
	run \
    -t \
    --rm \
    -v /data/jenkins/home/jobs/afp4mule/jobs/afp4Mule-sf-deploy/workspace/:/root/workdir/artifacts/ \
    docker.accenture.com/adop/tokenization:0.0.1 perl /root/workdir/token_resolver_template.pl --tokenFile /root/workdir/artifacts/devops_envs/${ENVIRONMENT}.properties --tokenFile /root/workdir/artifacts/afp4mule_env_default.properties --fileList /root/workdir/artifacts/files_to_tokenise.txt --force

./docker \
	run \
    -t \
    --rm \
    -v /var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/afp4Mule-Deploy/:/root/workdir/artifacts/ \
    docker.accenture.com/adop/tokenization:0.0.1 perl /root/workdir/token_resolver_template.pl --tokenFile /root/workdir/artifacts/devops_envs/${ENVIRONMENT}.properties --tokenFile /root/workdir/artifacts/afp4mule_env_default.properties --fileList /root/workdir/artifacts/files_to_tokenise.txt --force

echo
echo "***************************************"
echo  "Packaging tokenized build(s)"
echo "***************************************"


ARTIFACTS=$(find artifacts/ -maxdepth 1 -mindepth 1 -type d)

for ARTIFACT in ${ARTIFACTS}
do
  cd $ARTIFACT
  zip -r ${WORKSPACE}/tokenized/$(basename $ARTIFACT) ./*
done

echo
echo "***************************************"
echo  "Deploying artifact(s)"
echo "***************************************"


ARTIFACTS=$(find ${WORKSPACE}/tokenized/ -name *.zip)

for ARTIFACT in ${ARTIFACTS}
do
  scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $ARTIFACT ec2-user@${MULE_EE_SERVER_IP}:~/
done

ssh -tt -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ec2-user@${MULE_EE_SERVER_IP} '
      sudo docker --config=/home/ec2-user/docker_auth_custom_registry --tlsverify --tlscacert=/home/ec2-user/.certs/ca.pem --tlscert=/home/ec2-user/.certs/cert.pem --tlskey=/home/ec2-user/.certs/key.pem -H manager.adop.internal:3376 stop mule-runtime
      sudo rm -rf /data/mule/apps/*
      for i in $(find ~/. -name *.zip)
      do
        sudo unzip ${i} -d /data/mule/apps/$(basename ${i} .zip) 1>/dev/null
      done

      sudo docker --config=/home/ec2-user/docker_auth_custom_registry --tlsverify --tlscacert=/home/ec2-user/.certs/ca.pem --tlscert=/home/ec2-user/.certs/cert.pem --tlskey=/home/ec2-user/.certs/key.pem -H manager.adop.internal:3376 start mule-runtime
'

echo
echo "***************************************"
echo  "Artifact(s) deployed"
echo "***************************************"


echo
echo "***************************************"
echo  "Health Check"
echo "***************************************"

SLEEP_TIME="30"
COUNT=0

echo "Waiting for environment to become accessible."
until curl -sL -w "%{http_code}\\n" "http://mule.adop.internal:8084/api/" -o /dev/null | grep "200" &> /dev/null
do
    if [ "${COUNT}" == "10" ]
      then
      echo "Exititing Attempt ${COUNT} : Environment not accessible"
      exit 1
    fi

    echo "Attempt ${COUNT} : Environment not accessible, sleeping for ${SLEEP_TIME}"
    sleep "${SLEEP_TIME}"
    COUNT=$((COUNT+1))
done

echo
echo "***************************************"
echo  "Artifact(s) deployed succesfully"
echo "***************************************"

''')
    }
}
