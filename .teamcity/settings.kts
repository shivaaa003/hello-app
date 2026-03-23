import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script

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
}

object Build : BuildType({
    name = "Build"

    params {
        param("env.isProdBuild", "")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "set commit id"
            id = "set_commit_id"

            conditions {
                doesNotEqual("env.isProdBuild", "yes")
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
                namesAndTags = """
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/hello-app:%teamcity.build.branch%_%build.number%
                    088332244542.dkr.ecr.ap-south-1.amazonaws.com/hello-app:%teamcity.build.branch%_latest
                """.trimIndent()
                commandArgs = "--platform linux/amd64 --build-arg artifact_version=%env.COMMIT_ID% --build-arg build_version=%build.counter%"
            }
        }
    }
})
