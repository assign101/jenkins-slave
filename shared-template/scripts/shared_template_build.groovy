def projectName="shared-template"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfspbebank/shared-templates.git"
    }

    stage("Gradle build") {
      sh """
        ${GRADLE_HOME}/bin/gradle clean zipIt publish -Prelease=true -Pbuild.number=$BUILD_NUMBER 
      """
    }

    
}
