// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def fullPathProjectFolderName = "${PROJECT_NAME}"

// Jobs
def createMuleStack = freeStyleJob(fullPathProjectFolderName + "/Create_Mule_Stack")

// Setup job to create Mule environment
createMuleStack.with{
	steps {
		shell('''#!/bin/bash -ex
			echo "testing"
		''')
	}
}