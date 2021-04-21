def projectName="vision"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/vision.git"
    }

    stage("Generate Build Scripts") {
        sh """
        set +x
bash builderw.sh init

      """
        
    }

    stage("Gradle build") {
      sh """
        ${GRADLE5_HOME}/bin/gradle build-info clean build tag-build -Prelease=true -Pbuild.number=$BUILD_NUMBER -Pversion=$APP_VER -Dorg.gradle.java.home=\${JAVA11_HOME}
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

    stage("Docker push") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) {
          sh """
            docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
            set +x
            bash builderw.sh push

          """
        }
    }

    
}
