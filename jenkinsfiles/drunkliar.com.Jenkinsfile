// drunkliar.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/drunkliar.com
// IIS:  F:\\website\\drunkliar.com
// Note: static; GA4 shared with blowdank
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
                    repo: 'sniperd2k/drunkliar.com',
                    iisPath: 'F:\\website\\drunkliar.com',
                    siteName: 'drunkliar.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
