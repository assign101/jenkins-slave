def projectName="kpipeline"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/${projectName}.git"
    }

    stage("Kpipeline Build") {
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

    
}
