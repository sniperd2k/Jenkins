// sniperd.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/sniperd.com
// IIS:  F:\\website\\sniperd.com
// Note: test:gate = vitest + playwright before FileCopy
@Library('sniperd-jenkins') _

pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Checkout + gate + promote') {
            steps {
                deploySite(
                    repo: 'sniperd2k/sniperd.com',
                    iisPath: 'F:\\website\\sniperd.com',
                    siteName: 'sniperd.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
