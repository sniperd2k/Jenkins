// emmeunderwood.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/emmeunderwood.com
// IIS:  F:\\website\\emmeunderwood.com
// Note: static site
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
                    repo: 'sniperd2k/emmeunderwood.com',
                    iisPath: 'F:\\website\\emmeunderwood.com',
                    siteName: 'emmeunderwood.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
