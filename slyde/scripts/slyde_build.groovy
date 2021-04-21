def projectName="slyde"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/${projectName}.git"
    }

    stage("Gradle build") {
      sh """
        ${GRADLE5_HOME}/bin/gradle build-info clean build tag-build publish -Pbuild.number=$BUILD_NUMBER -Prelease=true -Dorg.gradle.java.home=\${JAVA8_HOME}
      """
    }

    stage("Push Build Tags") {
      sshagent(['STASH_CREDENTIALS']) {
        sh """
         git push origin :build/latest || true
         git push --tags
        """
      }
    }

    
}
