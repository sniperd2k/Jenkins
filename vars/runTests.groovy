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

        def pkgJson = readJSON file: pkg
        def scripts = (pkgJson.scripts instanceof Map) ? pkgJson.scripts.keySet() as Set : [] as Set

        def chosen = null
        if (scripts.contains('test:gate')) {
            chosen = 'test:gate'
        } else if (scripts.contains('test:all')) {
            chosen = 'test:all'
        } else if (scripts.contains('test')) {
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
