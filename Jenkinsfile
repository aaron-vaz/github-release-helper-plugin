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
                    sh("./gradlew build")
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts(artifacts: "**/build/libs/*.hpi", fingerprint: true, onlyIfSuccessful: true)
            junit(testResults: "**/build/test-results/test/*.xml", allowEmptyResults: false, keepLongStdio: true)
        }
    }
}
