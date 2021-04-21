def projectName="springboot-template"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfspbebank/${projectName}.git"
    }

    stage("Build") {
      sshagent(['STASH_CREDENTIALS']){ sh """
        ${GRADLE5_HOME}/bin/gradle clean build tag-build -x test -Prelease=true -Pbuild.number=$BUILD_NUMBER -Dorg.gradle.java.home=\${JAVA11_HOME}
      """} 
    }

    stage("Test") {
      sh """
        ${GRADLE5_HOME}/bin/gradle test -Dorg.gradle.java.home=\${JAVA11_HOME}
      """
    }

    stage("Sonar") {
      sh """
        ${GRADLE5_HOME}/bin/gradle sonarqube -x test -Psonarurl=http://sonarqube -PsystemProp.http.nonProxyHosts=* -Dorg.gradle.java.home=\${JAVA11_HOME}
      """
    }

    stage("Docker Push") {
      sshagent(['STASH_CREDENTIALS']){ withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) { sh """docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
        ${GRADLE5_HOME}/bin/gradle docker_push -Dorg.gradle.java.home=\${JAVA11_HOME}
      """}} 
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
