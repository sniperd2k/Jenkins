// avaunderwood.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/avaunderwood.com
// IIS:  F:\\website\\avaunderwood.com
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
                    repo: 'sniperd2k/avaunderwood.com',
                    iisPath: 'F:\\website\\avaunderwood.com',
                    siteName: 'avaunderwood.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
