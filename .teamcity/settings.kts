package HelloApp.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

object hello_app_build : BuildType({
    id = AbsoluteId("hello_app_build")
    name = "Build"

    params {
        param("env.COMMIT_ID", "")
        select("env.isProdBuild", "", label = "isProdBuild?", description = "Select 'Yes' if this build is for Prod.", display = ParameterDisplay.PROMPT,
                options = listOf("No", "Yes"))
    }

    vcs {
        root(DslContext.settingsRoot)     // ← This is the fix
    }

    steps {
        script {
            name = "Set Commit ID"
            id = "Set_Commit_ID"

            conditions {
                doesNotEqual("env.isProdBuild", "Yes")
            }
            scriptContent = """
                SHORT_COMMIT_HASH=${'$'}(git rev-parse --short HEAD)
                echo "##teamcity[setParameter name='env.COMMIT_ID' value='${'$'}SHORT_COMMIT_HASH']"
                echo "commit-id = ${'$'}SHORT_COMMIT_HASH"
            """.trimIndent()
        }

        dockerCommand {
            name = "dockerbuild"
            id = "dockerbuild"

            conditions {
                doesNotEqual("env.isProdBuild", "Yes")
            }
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

            conditions {
                doesNotEqual("env.isProdBuild", "Yes")
            }
            commandType = push {
                namesAndTags = """
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_%build.number%
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%teamcity.build.branch%_latest
                """.trimIndent()
                removeImageAfterPush = false
            }
        }

        step {
            name = "DeployDev"
            id = "DeployDev"
            type = "octopus.create.release"

            conditions {
                equals("teamcity.build.branch", "main")
                doesNotEqual("env.isProdBuild", "Yes")
            }
            param("octopus_additionalcommandlinearguments", """--variable="DockerTag=%teamcity.build.branch%_%build.number%"""")
            param("octopus_space_name", "OASIS")
            param("octopus_waitfordeployments", "true")
            param("octopus_channel_name", "Development")
            param("octopus_version", "3.0+")
            param("octopus_host", "http://10.29.1.84")
            param("octopus_project_name", "hello-app")
            param("octopus_deployto", "Development")
            param("secure:octopus_apikey", "******")
            param("octopus_releasenumber", "%build.number%")
        }

        step {
            name = "DeployStage"
            id = "DeployStage"
            type = "octopus.create.release"

            conditions {
                equals("teamcity.build.branch", "stage")
                doesNotEqual("env.isProdBuild", "Yes")
            }
            param("octopus_additionalcommandlinearguments", """--variable="DockerTag=%teamcity.build.branch%_%build.number%"""")
            param("octopus_space_name", "OASIS")
            param("octopus_waitfordeployments", "true")
            param("octopus_channel_name", "Stage")
            param("octopus_version", "3.0+")
            param("octopus_host", "http://10.29.1.84")
            param("octopus_project_name", "hello-app")
            param("octopus_deployto", "Stage")
            param("secure:octopus_apikey", "******")
            param("octopus_releasenumber", "%build.number%")
        }

        step {
            name = "DeployUAT"
            id = "DeployUAT"
            type = "octopus.create.release"

            conditions {
                equals("teamcity.build.branch", "uat")
                doesNotEqual("env.isProdBuild", "Yes")
            }
            param("octopus_additionalcommandlinearguments", """--variable="DockerTag=%teamcity.build.branch%_%build.number%"""")
            param("octopus_space_name", "OASIS")
            param("octopus_waitfordeployments", "true")
            param("octopus_channel_name", "Uat")
            param("octopus_version", "3.0+")
            param("octopus_host", "http://10.29.1.84")
            param("octopus_project_name", "hello-app")
            param("octopus_deployto", "Uat")
            param("secure:octopus_apikey", "******")
            param("octopus_releasenumber", "%build.number%")
        }

        script {
            name = "Retag_UAT_image_for_Prod"
            id = "Retag_release_branch_image_for_Prod"

            conditions {
                equals("teamcity.build.branch", "uat")
                equals("env.isProdBuild", "Yes")
            }
            scriptContent = """
                docker pull 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest
                docker tag 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest 088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_%build.number%
                docker tag 088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:uat_latest 088332244542.dkr.ecr.ap-south-1.amazonaws.com/prod/hello-app:prod_latest
            """.trimIndent()
        }

        dockerCommand {
            name = "Push_Prod_image"
            id = "Push_Prod_image"

            conditions {
                equals("teamcity.build.branch", "uat")
                equals("env.isProdBuild", "Yes")
            }
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
                dockerRegistryId = "PROJECT_EXT_24,PROJECT_EXT_30"   // ← Change to your actual Docker registry connection IDs in TeamCity
            }
        }
    }
})
