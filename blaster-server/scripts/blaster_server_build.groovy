def projectName="blaster-server"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfsninja/${projectName}.git"
    }

    stage("Download Node-Sass") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'LEGACY_NEXUS_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) { sh """
        wget --no-check-certificate https://${NEXUS_USER}:${NEXUS_PASS}@nexus.internal.macquarie.com/content/repositories/bfs-releases/com/mgl/digital/sds/binaries/binding-node/1.0.0/linux-x64-64-binding-node.tar.gz
            tar -xvzf linux-x64-64-binding-node.tar.gz
            npm config set sass_binary_path `pwd`/linux-x64-64_binding.node

      """
        } 
    }

    stage("Script Execution - $projectName") {
        sh """
        set +x
bash build-package-ui.sh

      """
        
    }

    stage("Gradle build") {
      sh """
        ${GRADLE5_HOME}/bin/gradle clean build tag-build -Dorg.gradle.java.home=\${JAVA8_HOME}
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
bash builder.sh push

          """
        }
    }

    
}
