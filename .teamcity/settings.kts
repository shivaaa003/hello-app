import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2025.11"

project {

    val hello_app_build = BuildType {
        id = AbsoluteId("HelloApp_hello_app_build")
        name = "Build"

        params {
            param("env.COMMIT_ID", "")
            select("env.isProdBuild", "No", label = "isProdBuild?", description = "Select Yes only for Production", display = ParameterDisplay.PROMPT,
                    options = listOf("No", "Yes"))
        }

        vcs {
            root(DslContext.settingsRoot)
        }

        steps {
            script {
                name = "Set Commit ID"
                id = "Set_Commit_ID"
                scriptContent = """
                    SHORT_COMMIT_HASH=${'$'}(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='env.COMMIT_ID' value='${'$'}SHORT_COMMIT_HASH']"
                    echo "commit-id = ${'$'}SHORT_COMMIT_HASH"
                """.trimIndent()
            }

            dockerCommand {
                name = "dockerbuild"
                id = "dockerbuild"
                commandType = build {
                    source = file {
                        path = "Dockerfile"
                    }
                    contextDir = "."
                    platform = DockerCommandStep.ImagePlatform.Linux
                    namesAndTags = """
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_%build.number%
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_latest
                    """.trimIndent()
                    commandArgs = "--platform linux/amd64 --build-arg artifact_version=%env.COMMIT_ID% --build-arg build_version=%build.counter%"
                }
            }

            dockerCommand {
                name = "docker image push"
                id = "docker_image_push"
                commandType = push {
                    namesAndTags = """
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_%build.number%
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_latest
                    """.trimIndent()
                    removeImageAfterPush = false
                }
            }

            // Simple Deploy Steps for demonstration
            step {
                name = "DeployDev"
                id = "DeployDev"
                type = "octopus.create.release"
                param("octopus_space_name", "OASIS")
                param("octopus_host", "http://10.29.1.84")
                param("octopus_project_name", "hello-app")
                param("octopus_channel_name", "Development")
                param("octopus_deployto", "Development")
                param("octopus_releasenumber", "%build.number%")
                param("secure:octopus_apikey", "******")
            }

            step {
                name = "DeployStage"
                id = "DeployStage"
                type = "octopus.create.release"
                param("octopus_space_name", "OASIS")
                param("octopus_host", "http://10.29.1.84")
                param("octopus_project_name", "hello-app")
                param("octopus_channel_name", "Stage")
                param("octopus_deployto", "Stage")
                param("octopus_releasenumber", "%build.number%")
                param("secure:octopus_apikey", "******")
            }

            step {
                name = "DeployUAT"
                id = "DeployUAT"
                type = "octopus.create.release"
                param("octopus_space_name", "OASIS")
                param("octopus_host", "http://10.29.1.84")
                param("octopus_project_name", "hello-app")
                param("octopus_channel_name", "Uat")
                param("octopus_deployto", "Uat")
                param("octopus_releasenumber", "%build.number%")
                param("secure:octopus_apikey", "******")
            }

            // Prod Promotion
            script {
                name = "Retag_UAT_image_for_Prod"
                id = "Retag_release_branch_image_for_Prod"
                scriptContent = """
                    docker pull 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest
                    docker tag 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest 088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_%build.number%
                    docker tag 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest 088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_latest
                """.trimIndent()
            }

            dockerCommand {
                name = "Push_Prod_image"
                id = "Push_Prod_image"
                commandType = push {
                    namesAndTags = """
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_%build.number%
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_latest
                    """.trimIndent()
                    removeImageAfterPush = false
                }
            }
        }

        triggers {
            vcs {
                branchFilter = """
                    +:main
                    +:stage
                    +:uat
                """.trimIndent()
            }
        }

        features {
            perfmon { }
            dockerRegistryConnections {
                loginToRegistry = on {
                    dockerRegistryId = "PROJECT_EXT_24,PROJECT_EXT_30"
                }
            }
        }
    }

    buildType(hello_app_build)
}
