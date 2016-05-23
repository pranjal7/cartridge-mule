// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"


// Variables
// **The git repo variables will be changed to the users' git repositories manually in the Jenkins jobs**
def environmentTemplateGitUrl = "git@innersource.accenture.com:digital-1-cartridges/mule_environment_template.git"

// Jobs
def createMuleStack = freeStyleJob(projectFolderName + "/Create_Mule_Stack")

// Setup setup_cartridge
createMuleStack.with{
	description("Job to provision the envirnment for Mule")
	logRotator {
		numToKeep(25)
    }
	parameters{
		stringParam("GIT_URL","git@innersource.accenture.com:digital-1-cartridges/mule_environment_template.git","The URL of the git repo for Platform Extension")
		stringParam("STACK_NAME","","The name of the new stack")
        stringParam("KEY_NAME","","Name of the key for this stack")
		credentialsParam("AWS_CREDENTIALS"){
			type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
			description('AWS access key and secret key for your account')
		}
	}
	label("aws")
	environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
    }
	scm{
		git{
			remote{
				name("origin")
				url("${environmentTemplateGitUrl}")
				credentials("adop-jenkins-master")
			}
			branch("*/master")
		}
	}
	wrappers {
		preBuildCleanup()
		injectPasswords()
		maskPasswords()
		sshAgent("adop-jenkins-master")
		credentialsBinding {
		  usernamePassword("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", '${AWS_CREDENTIALS}')
		}
	}
	steps {
		shell('''#!/bin/bash -ex

			# Variables
			export AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION
			INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
			PUBLIC_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4
			if [ -z $VPC_ID ]; then
				echo "VPC ID not set, using default VPC where ADOP is deployed..."
				VPC_ID=$(aws ec2 describe-instances --instance-ids ${INSTANCE_ID} --query 'Reservations[0].Instances[0].VpcId' --output text);
			fi
			
			#ADOP_CIDR=$(aws ec2 describe-subnets --subnet-ids ${AWS_SUBNET_ID} --query 'Subnets[0].CidrBlock' --output text)
			#ADOP_AZ=$(aws ec2 describe-instances --instance-ids ${INSTANCE_ID} --query 'Reservations[0].Instances[0].Placement.AvailabilityZone' --output text)
			#ADOP_IP=$(aws ec2 describe-instances --instance-ids ${INSTANCE_ID} --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
			
			
			aws cloudformation create-stack --stack-name ${STACK_NAME} \
			--tags "Key=CreatedBy,Value=ADOP-Jenkins" \
			--template-body file://aws/aws_mule_template.json \
			--parameters \
			ParameterKey=NatInstanceId,ParameterValue=${INSTANCE_ID} \
			ParameterKey=PublicIp,ParameterValue=${PUBLIC_IP} \
			ParameterKey=VpcId,ParameterValue=${VPC_ID} \
			ParameterKey=KeyName,ParameterValue=${KEY_NAME}
			
			# Keep looping whilst the stack is being created
				SLEEP_TIME=60
				COUNT=0
				TIME_SPENT=0
				while aws cloudformation describe-stacks --stack-name ${STACK_NAME} | grep -q "CREATE_IN_PROGRESS" > /dev/null
			do
				TIME_SPENT=$(($COUNT * $SLEEP_TIME))
				echo "Attempt ${COUNT} : Stack creation in progress (Time spent : ${TIME_SPENT} seconds)"
				sleep "${SLEEP_TIME}"
				COUNT=$((COUNT+1))
			done
        
			# Check that the stack created
			TIME_SPENT=$(($COUNT * $SLEEP_TIME))
			if $(aws cloudformation describe-stacks --stack-name ${STACK_NAME} | grep -q "CREATE_COMPLETE")
			then
				echo "Stack has been created in approximately ${TIME_SPENT} seconds."
				NODE_IP=$(aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query 'Stacks[].Outputs[?OutputKey==`EC2InstancePrivateIp`].OutputValue' --output text)
				NEW_INSTANCE_ID=$(aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query 'Stacks[].Outputs[?OutputKey==`EC2InstanceID`].OutputValue' --output text)
			else
				echo "ERROR : Stack creation failed after ${TIME_SPENT} seconds. Please check the AWS console for more information."
				exit 1
			fi
        
			echo "Success! The private IP of your new EC2 instance is $NODE_IP"
			echo "Please use your provided key, ${KEY_NAME}, in order to SSH onto the instance."
			
			# Keep looping whilst the EC2 instance is still initializing
			COUNT=0
			TIME_SPENT=0
			while aws ec2 describe-instance-status --instance-ids ${NEW_INSTANCE_ID} | grep -q "initializing" > /dev/null
			do
				TIME_SPENT=$(($COUNT * $SLEEP_TIME))
				echo "Attempt ${COUNT} : EC2 Instance still initializing (Time spent : ${TIME_SPENT} seconds)"
				sleep "${SLEEP_TIME}"
				COUNT=$((COUNT+1))
			done
        
			# Check that the instance has initalized and all tests have passed
			TIME_SPENT=$(($COUNT * $SLEEP_TIME))
			if $(aws ec2 describe-instance-status --instance-ids ${NEW_INSTANCE_ID} --query 'InstanceStatuses[0].InstanceStatus' --output text | grep -q "passed")
			then
				echo "Instance has been initialized in approximately ${TIME_SPENT} seconds."
				echo "Please change your default security group depending on the level of access you wish to enable."
			else
				echo "ERROR : Instance initialization failed after ${TIME_SPENT} seconds. Please check the AWS console for more information."
				exit 1
			fi
			     

		''')
	}
	
}