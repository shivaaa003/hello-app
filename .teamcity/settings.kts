import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.projectFeatures.dockerECRRegistry

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2025.11"

project {
    description = """
        this is to understand the ci on the teamcity 
        this is minicry of oasis project
    """.trimIndent()

    buildType(Build)

    params {
        param("env.COMMIT_ID", "dummy")
    }

    features {
        dockerECRRegistry {
            id = "PROJECT_EXT_3"
            displayName = "Amazon ECR"
            ecrType = ecrPrivate()
            registryId = "088332244542"
            credentialsProvider = accessKey {
                accessKeyId = "AKIARJEIDCY7JZ7KBAOD"
                secretAccessKey = "credentialsJSON:416a74ce-89d1-4ca3-85ae-ddf09572e62c"
            }
            regionCode = "ap-south-1"
            credentialsType = accessKeys()
        }
    }
}

object Build : BuildType({
    name = "Build"

    params {
        param("env.COMMIT_ID", "")
        param("env.BRANCH_CLEAN", "%teamcity.build.vcs.branch.replace('refs/heads/','').replace('/','-')%")
        param("env.isProdBuild", "")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        dockerCommand {
            name = "docker image push"
            id = "docker_image_push"

            conditions {
                doesNotEqual("env.isProdBuild", "Yes")
            }
            commandType = push {
                namesAndTags = """
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/hello-app:%build.number%
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/hello-app:latest
                """.trimIndent()
            }
        }
        step {
            name = "DeployDev"
            id = "DeployDev"
            type = "octopus.create.release"

            conditions {
                doesNotEqual("env.isProdBuild", "Yes")
            }
            param("octopus_additionalcommandlinearguments", """--variable="DockerTag=%build.number%"""")
            param("octopus_space_name", "Default")
            param("octopus_channel_name", "Development")
            param("octopus_version", "3.0+")
            param("octopus_host", "https://poc01.octopus.app")
            param("octopus_project_name", "hello-app")
            param("octopus_deployto", "Development")
            param("secure:octopus_apikey", "credentialsJSON:26316b1d-1617-413d-a31f-30f4d6f19b78")
            param("octopus_releasenumber", "%build.number%")
        }
    }

    features {
        dockerRegistryConnections {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_3"
            }
        }
    }
})
