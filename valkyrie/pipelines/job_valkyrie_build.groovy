def projectName="valkyrie"
folder("tools"){}
pipelineJob("tools/$projectName-build") {
    description("Installs/upgrades $projectName in the cluster.")
    logRotator(-1, 10)
    parameters {
        stringParam("DEPLOYMENT_BRANCH", "release/1.0.0", "$projectName app branch for build")
        stringParam("PIPELINE_BRANCH", "master", "Jenkins Pipeline branch to run deployment script")
    }
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url "ssh://git@stash.internal.macquarie.com/bfssds/jenkins-pipelines.git"
                        branch '$PIPELINE_BRANCH'
                        credentials "STASH_CREDENTIALS"
                    }
                }
            }
            scriptPath "generated/builds/tools/valkyrie/scripts/valkyrie_build.groovy"
        }
    }
    configure {
        it / definition / lightweight(false)
    }
}