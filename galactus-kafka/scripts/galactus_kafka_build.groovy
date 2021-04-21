def projectName="galactus-kafka"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/bfsninja/sds-cloud-pipeline.git"
    }

    stage("Gradle build") {
      sh """
        ${GRADLE_HOME}/bin/gradle -Pbuild.number=$BUILD_NUMBER -Prelease=true clean build uploadArchives -Dorg.gradle.java.home=\${JAVA11_HOME}
      """
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
