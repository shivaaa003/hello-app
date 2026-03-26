import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script

version = "2025.11"

project {
    val helloAppBuild = BuildType {
        id = AbsoluteId("HelloApp_hello_app_build")
        name = "Build"

        params {
            param("env.COMMIT_ID", "")
            // Optional: keep this if you want to choose prod manually later
            select("env.isProdBuild", "No", label = "isProdBuild?", 
                   options = listOf("No", "Yes"))
        }

        vcs {
            root(DslContext.settingsRoot)
        }

        steps {
            // 1. Set short commit hash
            script {
                name = "Set Commit ID"
                scriptContent = """
                    SHORT_COMMIT_HASH=$(git rev-parse --short HEAD)
                    echo "##teamcity[setParameter name='env.COMMIT_ID' value='$SHORT_COMMIT_HASH']"
                    echo "commit-id = $SHORT_COMMIT_HASH"
                """.trimIndent()
            }

            // 2. Docker Build (non-prod only for now)
            dockerCommand {
                name = "Docker Build"
                commandType = build {
                    source = file { path = "Dockerfile" }
                    contextDir = "."
                    platform = DockerCommandStep.ImagePlatform.Linux
                    namesAndTags = """
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%build.number%
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:latest
                    """.trimIndent()
                    commandArgs = "--platform linux/amd64 --build-arg artifact_version=%env.COMMIT_ID% --build-arg build_version=%build.counter%"
                }
            }

            // 3. Docker Push (non-prod)
            dockerCommand {
                name = "Docker Push (non-prod)"
                commandType = push {
                    namesAndTags = """
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:%build.number%
                        088332244542.dkr.ecr.ap-south-1.amazonaws.com/non-prod/hello-app:latest
                    """.trimIndent()
                    removeImageAfterPush = false
                }
            }

            // 4. Simple Octopus Create Release (only to Development for learning)
            step {
                name = "Create Release in Octopus (Development)"
                type = "octopus.create.release"
                param("octopus_host", "https://poc01.octopus.app")
                param("octopus_space_name", "default")
                param("octopus_project_name", "hello-app")
                param("octopus_releasenumber", "%build.number%")
                // Optional: deploy immediately to an environment
                // param("octopus_deployto", "Development")   // uncomment if you want auto-deploy
                param("secure:octopus_apikey", "your-actual-api-key-here")  // ← Replace with real key
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
                    dockerRegistryId = "PROJECT_EXT_24,PROJECT_EXT_30"  // keep your existing registry connections
                }
            }
        }
    }

    buildType(helloAppBuild)
}
