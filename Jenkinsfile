def getRepoURL() {
    return sh(script: "git config --get remote.origin.url", returnStdout: true).trim()
}

def getCommitID() {
    return sh(script: "git rev-parse HEAD", returnStdout: true).trim()
}

pipeline {
    agent any

    stages {
        stage("Checkout") {
            steps {
                checkout scm
                sh("git reset --hard")
                sh("git clean -fdx")
            }
        }

        stage("Build") {
            steps {
                script {
                    sh("./gradlew ci")
                    currentBuild.displayName = readFile("${env.WORKSPACE}/build/version.txt").trim()
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts(artifacts: "**/build/libs/*.hpi", fingerprint: true, onlyIfSuccessful: true)
            junit(testResults: "**/build/test-results/test/*.xml", allowEmptyResults: false, keepLongStdio: true)
            jacoco(execPattern: "**/build/jacoco/test.exec")
        }

        success {
            script {
                if(env.BRANCH_NAME == "master") {
                    githubRelease(repoURL: getRepoURL(),
                        releaseTag: "v${currentBuild.displayName}",
                        commitish: getCommitID(),
                        releaseName: "Release ${currentBuild.displayName}",
                        releaseBody: "",
                        isPreRelease: false,
                        isDraftRelease: false,
                        artifactPatterns: "**/build/libs/*.hpi")
                }
            }
        }
    }
}
