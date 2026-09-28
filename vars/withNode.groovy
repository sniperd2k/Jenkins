/**
 * Run a closure with PATH/NODE_HOME pinned to the host Node install.
 * Default pin: C:\grok\tools\node (v22.19.0 on COMPUTER).
 *
 * Usage:
 *   withNode { bat 'npm run test:gate' }
 *   withNode(nodeHome: 'C:\\grok\\tools\\node') { ... }
 */
def call(Closure body) {
    call([:], body)
}

def call(Map args, Closure body) {
    def nodeHome = args.nodeHome ?: 'C:\\grok\\tools\\node'
    def playwrightBrowsers = args.playwrightBrowsers ?: 'C:\\grok\\tools\\playwright-browsers'

    withEnv([
        "NODE_HOME=${nodeHome}",
        "PATH=${nodeHome};${nodeHome}\\node_modules\\.bin;${env.PATH}",
        "PLAYWRIGHT_BROWSERS_PATH=${playwrightBrowsers}"
    ]) {
        echo "withNode: NODE_HOME=${nodeHome} (expected v22.19.0)"
        body()
    }
}
