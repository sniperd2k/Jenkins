// blowdank.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/drunkliar.com
// IIS:  F:\\website\\blowdank.com
// Note: no dedicated repo — shares sniperd2k/drunkliar.com
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
                    iisPath: 'F:\\website\\blowdank.com',
                    siteName: 'blowdank.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
