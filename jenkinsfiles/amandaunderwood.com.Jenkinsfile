// amandaunderwood.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/amandaunderwood.com
// IIS:  F:\\website\\amandaunderwood.com
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
                    repo: 'sniperd2k/amandaunderwood.com',
                    iisPath: 'F:\\website\\amandaunderwood.com',
                    siteName: 'amandaunderwood.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
