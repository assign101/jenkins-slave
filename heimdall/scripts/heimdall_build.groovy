def projectName="heimdall"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/${projectName}.git"
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

    stage("Docker push") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) {
          sh """
            docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
            set +x
            bash builder.sh ${APP_VER}.${BUILD_NUMBER}

          """
        }
    }

    
}
