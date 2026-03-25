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
        root(HelloApp_HttpsGithubComShivaaa003HelloAppGitRefsHeadsMain)   // ← CHANGE TO YOUR REAL VCS ROOT ID
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

        // DeployDev, DeployStage, DeployUAT steps remain exactly the same as before
        // (I omitted them here for brevity — keep the ones from my previous message)

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
                dockerRegistryId = "PROJECT_EXT_24,PROJECT_EXT_30"   // ← update with your real Docker connection IDs
            }
        }
    }
})
