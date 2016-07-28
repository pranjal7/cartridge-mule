// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables

// Jobs
def deleteMuleStack = freeStyleJob(projectFolderName + "/Delete_Mule_Stack")

// Delete Mule Stack
deleteMuleStack.with{
	description("Job to delete the Mule Stack on AWS")
	logRotator {
		numToKeep(7)
    }
	parameters{
		stringParam("STACK_NAME","D1SE-Mule","The name of the stack to delete")
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
			
			aws cloudformation delete-stack --stack-name ${STACK_NAME} \
			
			# Keep looping whilst the stack is being deleted
				SLEEP_TIME=60
				COUNT=0
				TIME_SPENT=0
				while aws cloudformation describe-stacks --stack-name ${STACK_NAME} | grep -q "DELETE_IN_PROGRESS" > /dev/null
			do
				TIME_SPENT=$(($COUNT * $SLEEP_TIME))
				echo "Attempt ${COUNT} : Stack deletion in progress (Time spent : ${TIME_SPENT} seconds)"
				sleep "${SLEEP_TIME}"
				COUNT=$((COUNT+1))
			done
        
			# Check that the stack has been deleted
			TIME_SPENT=$(($COUNT * $SLEEP_TIME))
			if $(aws cloudformation describe-stacks --stack-name ${STACK_NAME})
			then
				echo "Stack has been deleted in approximately ${TIME_SPENT} seconds."
			else
				echo "ERROR : Stack deletion failed after ${TIME_SPENT} seconds. Please check the AWS console for more information."
				exit 1
			fi
			
		''')
	}
	
}