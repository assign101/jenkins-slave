def projectName="devcon"
node {
    stage("Clone and checkout source code for $projectName") {
        git branch: "${DEPLOYMENT_BRANCH}", credentialsId: "STASH_CREDENTIALS",
                url: "ssh://git@stash.internal.macquarie.com/BFSNINJA/devcon.git"
    }

    stage("Gradle build") {
      sh """
        ${GRADLE5_HOME}/bin/gradle clean build -Dorg.gradle.java.home=\${JAVA11_HOME}
      """
    }

    stage("Docker push") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NEXUS3_CREDENTIALS',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS']]) {
          sh """
            docker login --username=${NEXUS_USER} --password=${NEXUS_PASS} https://nexus.devtools.syd.c1.macquarie.com:9991
            set +x
            cd docker
            bash builder.sh $APP_VER.$BUILD_NUMBER push

          """
        }
    }

    
}
