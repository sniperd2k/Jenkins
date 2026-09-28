// moodybeachmaine.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/moodybeachmaine.com
// IIS:  F:\\website\\moodybeachmaine.com
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
                    repo: 'sniperd2k/moodybeachmaine.com',
                    iisPath: 'F:\\website\\moodybeachmaine.com',
                    siteName: 'moodybeachmaine.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
