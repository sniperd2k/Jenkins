/**
 * Checkout (optional) + runTests gate + FileCopy-style promote to IIS path.
 * Preserves existing App_Data\\*.json on the target (guestbook / ASP.NET state).
 *
 * Required:
 *   iisPath  — e.g. 'F:\\website\\sniperd.com'
 *
 * Optional:
 *   repo     — 'sniperd2k/sniperd.com' (owner/name). If set, checks out into workspace.
 *   gitUrl   — full HTTPS URL (overrides repo).
 *   branch   — default env.BRANCH_NAME or 'master'
 *   credentialsId — Jenkins credential ID (placeholder default: 'github-sniperd2k')
 *   excludes — extra directory names to skip (default node_modules, .git, tests, …)
 *   skipTests — if true, skip runTests (NOT recommended)
 *   siteName — label for logs
 *
 * Standing rule: red tests block FileCopy (sniperd gate pattern).
 *
 * Usage (from a site Jenkinsfile stage):
 *   deploySite(repo: 'sniperd2k/sniperd.com', iisPath: 'F:\\website\\sniperd.com')
 */
def call(Map args) {
    if (!args?.iisPath) {
        error 'deploySite: iisPath is required (e.g. F:\\\\website\\\\sniperd.com)'
    }

    def iisPath = args.iisPath
    def siteName = args.siteName ?: args.repo ?: iisPath
    def branch = args.branch ?: (env.BRANCH_NAME ?: 'master')
    def credentialsId = args.credentialsId ?: 'github-sniperd2k'
    def skipTests = args.skipTests == true
    def excludes = args.excludes instanceof List ? args.excludes : [
        'node_modules', '.git', '.github', 'tests', 'e2e', 'test-results',
        'playwright-report', 'coverage', '.vscode', '.idea'
    ]

    echo "=== deploySite: ${siteName} → ${iisPath} (branch=${branch}) ==="

    def gitUrl = args.gitUrl
    if (!gitUrl && args.repo) {
        gitUrl = "https://github.com/${args.repo}.git"
    }
    if (gitUrl) {
        echo "Checkout ${gitUrl} branch */${branch} (credentialsId=${credentialsId})"
        checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            userRemoteConfigs: [[
                url: gitUrl,
                credentialsId: credentialsId
            ]]
        ])
    } else {
        echo 'deploySite: no repo/gitUrl — using current workspace (checkout scm)'
        checkout scm
    }

    if (!skipTests) {
        echo '=== Test gate (must pass before FileCopy) ==='
        runTests()
    } else {
        echo 'WARNING: deploySite skipTests=true — FileCopy will run without gate'
    }

    echo "=== Promote (FileCopy-style) → ${iisPath} ==="
    _promote(iisPath, excludes)

    echo "=== deploySite done: ${siteName} → ${iisPath} ==="
}

private void _promote(String iisPath, List excludes) {
    if (isUnix()) {
        echo "WARNING: Unix agent — rsync-like copy; production promote is Windows robocopy to ${iisPath}"
        def excludeArgs = excludes.collect { "--exclude ${it}" }.join(' ')
        sh """
            mkdir -p '${iisPath}'
            if [ -d '${iisPath}/App_Data' ]; then
              mkdir -p /tmp/app_data_preserve && cp -a '${iisPath}/App_Data/'*.json /tmp/app_data_preserve/ 2>/dev/null || true
            fi
            rsync -a ${excludeArgs} ./ '${iisPath}/'
            if [ -d /tmp/app_data_preserve ]; then
              mkdir -p '${iisPath}/App_Data'
              cp -a /tmp/app_data_preserve/*.json '${iisPath}/App_Data/' 2>/dev/null || true
            fi
        """
        return
    }

    def xd = excludes.collect { "/XD ${it}" }.join(' ')
    bat """
        @echo off
        setlocal EnableExtensions
        set "TARGET=${iisPath}"
        if not exist "%TARGET%" mkdir "%TARGET%"

        rem --- preserve App_Data *.json ---
        set "PRESERVE=%TEMP%\\jenkins-appdata-preserve-%RANDOM%"
        if exist "%TARGET%\\App_Data" (
          mkdir "%PRESERVE%" 2>nul
          copy /Y "%TARGET%\\App_Data\\*.json" "%PRESERVE%\\" >nul 2>&1
        )

        rem --- FileCopy-style promote (robocopy 0-7 = success) ---
        robocopy . "%TARGET%" /E /NFL /NDL /NJH /NJS /nc /ns /np ${xd} /XF .gitignore .gitattributes
        set RC=%ERRORLEVEL%
        if %RC% GEQ 8 (
          echo robocopy failed with exit %RC%
          exit /b %RC%
        )

        rem --- restore App_Data *.json ---
        if exist "%PRESERVE%" (
          if not exist "%TARGET%\\App_Data" mkdir "%TARGET%\\App_Data"
          copy /Y "%PRESERVE%\\*.json" "%TARGET%\\App_Data\\" >nul 2>&1
          rmdir /S /Q "%PRESERVE%" 2>nul
        )

        rem --- ACL loosen like legacy freestyle cacls step ---
        cacls "%TARGET%" /t /e /g Everyone:f >nul 2>&1
        echo Promote complete: %TARGET%
        exit /b 0
    """
}
