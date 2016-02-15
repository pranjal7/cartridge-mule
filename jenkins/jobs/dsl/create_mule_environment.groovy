// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def fullPathProjectFolderName = "${PROJECT_NAME}"

// Variables
def environmentTemplateGitUrl = "git@innersource.accenture.com:digital-1-cartridges/mule_environment_template.git"

// Jobs
def createMuleStack = freeStyleJob(fullPathProjectFolderName + "/Create_Mule_Stack")

// Setup job to create Mule environment
createMuleStack.with{
	parameters{
		stringParam("ENVIRONMENT_NAME","","The name of the environment to be created.")
		stringParam("KEY_NAME","mule_key","The name of the key pair to create the environment with")
	}
	label("master")
	environmentVariables {
		env('WORKSPACE_NAME',workspaceFolderName)
		env('PROJECT_NAME',fullPathProjectFolderName)
	}
	wrappers {
		preBuildCleanup()
		injectPasswords()
		maskPasswords()
		sshAgent("adop-jenkins-master")
		credentialsBinding {
		  usernamePassword("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "aws-environment-provisioning")
		}
	}
	steps {
		conditionalSteps {
		  condition {
			shell('test ! -f "${JENKINS_HOME}/tools/.aws/bin/aws"')
		  }
		  runner('Fail')
		  steps {
			shell('''set +x
					|mkdir -p ${JENKINS_HOME}/tools
					|wget https://s3.amazonaws.com/aws-cli/awscli-bundle.zip --quiet -O "${JENKINS_HOME}/tools/awscli-bundle.zip"
					|cd ${JENKINS_HOME}/tools && unzip -q awscli-bundle.zip
					|${JENKINS_HOME}/tools/awscli-bundle/install -i ${JENKINS_HOME}/tools/.aws
					|rm -rf ${JENKINS_HOME}/tools/awscli-bundle ${JENKINS_HOME}/tools/awscli-bundle.zip
					|set -x'''.stripMargin())
		  }
		}
	}
	steps {
		shell('''#!/bin/bash -ex

			export AWS_DEFAULT_REGION='eu-west-1'

			# Variables
			NAMESPACE=$( echo "${PROJECT_NAME}" | sed "s#[\/_ ]#-#g" )
			FULL_ENVIRONMENT_NAME="${NAMESPACE}-${ENVIRONMENT_NAME}"
			INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
			VPC_ID=$(${JENKINS_HOME}/tools/.aws/bin/aws ec2 describe-instances --instance-ids ${INSTANCE_ID}           --query 'Reservations[0].Instances[0].VpcId' --output text);
			HOST_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4)

			# Validate the stack
			environment_stack_name="${VPC_ID}-${FULL_ENVIRONMENT_NAME}"

			${JENKINS_HOME}/tools/.aws/bin/aws cloudformation create-stack --stack-name ${environment_stack_name} --tags "Key=createdby,Value=ADOP-Jenkins,Key=createdfor,Value=${NAMESPACE}" --template-body file://aws/aws_mule_template.json --parameters ParameterKey=KeyName,ParameterValue=${KEY_NAME} ParameterKey=NatInstanceId,ParameterValue=${INSTANCE_ID} ParameterKey=PublicIp,ParameterValue=${HOST_IP} ParameterKey=VpcId,ParameterValue=${VPC_ID}

			# Keep looping whilst the stack is being created
			SLEEP_TIME=60
			COUNT=0
			TIME_SPENT=0
			while ${JENKINS_HOME}/tools/.aws/bin/aws cloudformation describe-stacks --stack-name ${environment_stack_name} | grep -q "CREATE_IN_PROGRESS" > /dev/null
			do
				TIME_SPENT=$(($COUNT * $SLEEP_TIME))
				echo "Attempt ${COUNT} : Stack creation in progress (Time spent : ${TIME_SPENT} seconds)"
				sleep "${SLEEP_TIME}"
				COUNT=$((COUNT+1))
			done

			# Check that the stack created
			TIME_SPENT=$(($COUNT * $SLEEP_TIME))
			if $(${JENKINS_HOME}/tools/.aws/bin/aws cloudformation describe-stacks --stack-name ${environment_stack_name} | grep -q "CREATE_COMPLETE")
			then
				echo "Stack has been created in approximately ${TIME_SPENT} seconds."
			  NODE_IP=$(${JENKINS_HOME}/tools/.aws/bin/aws cloudformation describe-stacks --stack-name ${environment_stack_name} --query Stacks[].Outputs[].OutputValue --output text)
			else
				echo "ERROR : Stack creation failed after ${TIME_SPENT} seconds. Please check the AWS console for more information."
				exit 1
			fi

			echo "FULL_ENVIRONMENT_NAME=$FULL_ENVIRONMENT_NAME" > env.properties
			echo "NODE_IP=$NODE_IP" >> env.properties
		
		
		''')
	}
	scm {
		git{
			remote{
				name("origin")
				url("${environmentTemplateGitUrl}")
				credentials("adop-jenkins-master")
			}
			branch("*/develop")
		}
	}
}