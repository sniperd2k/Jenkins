// saynotoskiing.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/saynotoskiing.com
// IIS:  F:\\website\\saynotoskiing.com
// Note: do not modify site content from this repo
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
                    repo: 'sniperd2k/saynotoskiing.com',
                    iisPath: 'F:\\website\\saynotoskiing.com',
                    siteName: 'saynotoskiing.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
