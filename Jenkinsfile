// Self-test for sniperd2k/Jenkins (shared library config repo).
// Does NOT deploy sites — validates structure / smoke only.
pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Structure smoke') {
            steps {
                script {
                    def required = [
                        'README.md',
                        'vars/deploySite.groovy',
                        'vars/runTests.groovy',
                        'vars/withNode.groovy',
                        'jenkinsfiles/sniperd.com.Jenkinsfile',
                        'jenkinsfiles/davidunderwood.net.Jenkinsfile',
                        'jenkinsfiles/amandaunderwood.com.Jenkinsfile',
                        'jenkinsfiles/avaunderwood.com.Jenkinsfile',
                        'jenkinsfiles/emmeunderwood.com.Jenkinsfile',
                        'jenkinsfiles/moodybeachmaine.com.Jenkinsfile',
                        'jenkinsfiles/saynotoskiing.com.Jenkinsfile',
                        'jenkinsfiles/evilgame.com.Jenkinsfile',
                        'jenkinsfiles/drunkliar.com.Jenkinsfile',
                        'jenkinsfiles/blowdank.com.Jenkinsfile',
                    ]
                    def missing = required.findAll { !fileExists(it) }
                    if (missing) {
                        error "Missing required paths: ${missing.join(', ')}"
                    }
                    echo "Structure OK — ${required.size()} required paths present"

                    // Basic Groovy/text lint: files non-empty, contain expected tokens
                    def deploy = readFile('vars/deploySite.groovy')
                    def tests  = readFile('vars/runTests.groovy')
                    def node   = readFile('vars/withNode.groovy')
                    def readme = readFile('README.md')

                    assert deploy.contains('def call'), 'deploySite.groovy missing call()'
                    assert deploy.contains('App_Data'), 'deploySite must preserve App_Data'
                    assert deploy.contains('iisPath'), 'deploySite must take iisPath'
                    assert tests.contains('test:gate'), 'runTests must prefer test:gate'
                    assert tests.contains('package.json'), 'runTests must handle package.json'
                    assert node.contains('grok') && node.contains('tools') && node.contains('node'), 'withNode must pin C:\\grok\\tools\\node'
                    assert readme.contains('sniperd-jenkins'), 'README must document library name sniperd-jenkins'
                    assert readme.contains('G-'), 'README must list GA4 Measurement IDs'
                    echo 'Token lint OK'
                }
            }
        }
        stage('Site Jenkinsfile inventory') {
            steps {
                script {
                    def names = [
                        'davidunderwood.net', 'amandaunderwood.com', 'sniperd.com',
                        'avaunderwood.com', 'emmeunderwood.com', 'moodybeachmaine.com',
                        'saynotoskiing.com', 'evilgame.com', 'drunkliar.com', 'blowdank.com'
                    ]
                    echo "Checking ${names.size()} site Jenkinsfiles:"
                    names.each { n ->
                        def path = "jenkinsfiles/${n}.Jenkinsfile"
                        if (!fileExists(path)) {
                            error "Missing ${path}"
                        }
                        def body = readFile(path)
                        if (!body.contains("@Library('sniperd-jenkins')")) {
                            error "${path} missing @Library('sniperd-jenkins')"
                        }
                        if (!body.contains('deploySite(')) {
                            error "${path} must call deploySite(...)"
                        }
                        echo "  OK ${path}"
                    }
                }
            }
        }
    }
    post {
        success { echo 'sniperd2k/Jenkins self-test GREEN — ready for Ops Global Shared Library load' }
        failure { echo 'sniperd2k/Jenkins self-test RED — fix structure before Ops handoff' }
    }
}
