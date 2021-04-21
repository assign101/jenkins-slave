def projectName="gideon"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/${projectName}.git"
    }

    stage("Gideon Build") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'LEGACY_NEXUS_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) { sh """
        set +x
            bash vendor.sh download
            bash build.sh

      """
        } 
    }

    stage("Push Build Tags") {
      sshagent(['STASH_CREDENTIALS']) {
        sh """
         git push origin :build/latest || true
         git push --tags
        """
      }
    }

    stage("Docker build") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) {
          sh """
            docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
            set +x
    bash docker-build.sh push

          """
        }
    }

    
}
