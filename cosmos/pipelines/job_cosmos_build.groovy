def projectName="cosmos"
folder("tools"){}
pipelineJob("tools/$projectName-build") {
    description("Installs/upgrades $projectName in the cluster.")
    logRotator(-1, 10)
    parameters {
        stringParam("APP_VER", "2.0.0", "Version Tag for $projectName artifact")
        stringParam("DEPLOYMENT_BRANCH", "master", "Project $projectName branch for this build")
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
            scriptPath "generated/builds/tools/cosmos/scripts/cosmos_build.groovy"
        }
    }
    configure {
        it / definition / lightweight(false)
    }
}