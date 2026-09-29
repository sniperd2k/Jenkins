/**
 * Default pre-deploy gate (sniperd pattern): tests MUST pass before FileCopy.
 *
 * Preference order when package.json exists:
 *   1. npm run test:gate   (preferred — sniperd / evilgame)
 *   2. npm run test:all
 *   3. npm test
 *   4. package.json present but none of the above scripts → warn, continue
 *
 * No package.json → warn and continue (static HTML sites).
 * Any selected script that exits non-zero → fail the build (blocks deploy).
 *
 * Note: uses readFile + regex (no pipeline-utility-steps / readJSON required).
 *
 * Usage:
 *   runTests()
 *   runTests(dir: '.')
 */
def call(Map args = [:]) {
    def workDir = args.dir ?: '.'

    dir(workDir) {
        def pkg = 'package.json'
        if (!fileExists(pkg)) {
            echo "WARNING: runTests — no package.json in ${pwd()}; skipping tests (static site)."
            return
        }

        def text = readFile(pkg)
        def chosen = null
        if (text =~ /(?m)"test:gate"\s*:/) {
            chosen = 'test:gate'
        } else if (text =~ /(?m)"test:all"\s*:/) {
            chosen = 'test:all'
        } else if (text =~ /(?m)"test"\s*:/) {
            chosen = 'test'
        }

        if (!chosen) {
            echo "WARNING: runTests — package.json exists but no test:gate / test:all / test script; continuing without a gate."
            return
        }

        echo "runTests: running npm run ${chosen} (non-zero exit blocks deploy / FileCopy)"
        withNode {
            if (isUnix()) {
                sh "npm ci || npm install"
                sh "npm run ${chosen}"
            } else {
                bat "npm ci || npm install"
                bat "npm run ${chosen}"
            }
        }
    }
}
