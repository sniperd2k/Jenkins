// evilgame.com — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/evilgame.com
// IIS:  F:\\website\\evilgame.com
// Note: test:gate must pass before promote. Checkout uses credentialsId like every site (all site repos are private).
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
                    repo: 'sniperd2k/evilgame.com',
                    iisPath: 'F:\\website\\evilgame.com',
                    siteName: 'evilgame.com',
                    credentialsId: 'github-sniperd2k'
                )
            }
        }
    }
}
