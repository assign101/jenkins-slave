def projectName="jenkins-slave"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfssds/sds_data_image_builders.git"
    }

    
    stage("Generate $projectName values file") {
        ansiblePlaybook(inventory: "jenkins/ansible/inventory",
                        playbook: "jenkins/ansible/playbook.yml",
                        vaultCredentialsId: "ANSIBLE_VAULT",
                        extraVars: [ generated: "true" ])
    }

    stage("Docker push") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) {
          sh """
            docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
            set +x
cd jenkins/jnlp-slave-build
bash build.sh push

          """
        }
    }

    
}
