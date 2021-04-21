def projectName="cyberark-proxy"
folder("tools"){}
pipelineJob("tools/$projectName-build") {
    description("Installs/upgrades $projectName in the cluster.")
    logRotator(-1, 10)
    parameters {
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
            scriptPath "generated/builds/tools/cyberark-proxy/scripts/cyberark_proxy_build.groovy"
        }
    }
    configure {
        it / definition / lightweight(false)
    }
}