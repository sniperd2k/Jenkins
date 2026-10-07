// davidunderwood.net — thin site pipeline (shared library only)
// Repo: https://github.com/sniperd2k/davidunderwood.net
// IIS:  F:\\website\\davidunderwood.net
// Note: ASP.NET guestbook — App_Data/*.json preserved
// SEC-1: scripts/ never deployed; guestbook signing key never copied/overwritten
//        (lives on the server only); Everyone stripped from App_Data after cacls.
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
                    repo: 'sniperd2k/davidunderwood.net',
                    iisPath: 'F:\\website\\davidunderwood.net',
                    siteName: 'davidunderwood.net',
                    credentialsId: 'github-sniperd2k',
                    extraExcludes: ['scripts'],
                    excludeFiles: ['guestbook-signing-key*'],
                    protectPaths: ['App_Data']
                )
            }
        }
    }
}
