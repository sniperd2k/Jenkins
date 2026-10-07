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
 *   excludes — directory names to skip. REPLACES the defaults when passed
 *              (default node_modules, .git, .github, tests, e2e, test-results,
 *              playwright-report, coverage, .vscode, .idea)
 *   extraExcludes — directory names APPENDED to excludes/defaults (e.g. ['scripts']).
 *              robocopy /XD + rsync --exclude; matches that name at any depth.
 *   excludeFiles  — file names/wildcards APPENDED to robocopy /XF (always
 *              .gitignore .gitattributes) and rsync --exclude. Excluded files are
 *              never copied, so a live copy on the target is never overwritten
 *              (promote has no /MIR or --delete, so nothing is deleted either).
 *   protectPaths  — target subpaths (relative to iisPath, e.g. ['App_Data']) whose
 *              Everyone access is stripped AFTER the legacy cacls Everyone:f step.
 *              Windows, per path: icacls /inheritance:d (inherited ACEs copied as
 *              explicit, so AppPool/SYSTEM/Administrators keep access), then
 *              icacls /remove:g *S-1-1-0 (Everyone) on the path, then /T /C over
 *              the subtree, then verify no Everyone ACE remains. Fails the build if
 *              icacls errors on the path itself or Everyone is still present.
 *              Unix: no-op (the rsync promote does not loosen permissions).
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

    // Opt-in additions (all default to [] → promote is byte-for-byte unchanged).
    def extraExcludes = _nameList(args.extraExcludes, 'extraExcludes', false)
    if (extraExcludes) {
        excludes = (excludes + extraExcludes).unique()
        echo "deploySite: extraExcludes ${extraExcludes} (dirs skipped: ${excludes})"
    }
    def excludeFiles = _nameList(args.excludeFiles, 'excludeFiles', true)
    if (excludeFiles) {
        echo "deploySite: excludeFiles ${excludeFiles} (never copied / overwritten)"
    }
    def protectPaths = _protectList(args.protectPaths)
    if (protectPaths) {
        echo "deploySite: protectPaths ${protectPaths} (Everyone stripped after cacls)"
    }

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
    _promote(iisPath, excludes, excludeFiles, protectPaths)

    echo "=== deploySite done: ${siteName} → ${iisPath} ==="
}

private void _promote(String iisPath, List excludes, List excludeFiles, List protectPaths) {
    if (isUnix()) {
        echo "WARNING: Unix agent — rsync-like copy; production promote is Windows robocopy to ${iisPath}"
        def excludeArgs = excludes.collect { "--exclude ${it}" }.join(' ')
        if (excludeFiles) {
            // quoted so wildcards reach rsync instead of being globbed by sh
            excludeArgs += ' ' + excludeFiles.collect { "--exclude '${it}'" }.join(' ')
        }
        if (protectPaths) {
            echo "deploySite: protectPaths ${protectPaths} — Unix promote does not loosen permissions; nothing to strip"
        }
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
    def xfExtra = excludeFiles ? ' ' + excludeFiles.collect { "\"${it}\"" }.join(' ') : ''
    def protectBlock = protectPaths ? _protectBat(protectPaths) : ''
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
        robocopy . "%TARGET%" /E /NFL /NDL /NJH /NJS /nc /ns /np ${xd} /XF .gitignore .gitattributes${xfExtra}
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
        cacls "%TARGET%" /t /e /g Everyone:f >nul 2>&1${protectBlock}
        echo Promote complete: %TARGET%
        exit /b 0
    """
}

/**
 * Batch fragment run after the cacls Everyone:f step, once per protectPath.
 * Idempotent: /inheritance:d is a no-op once inheritance is already off, and
 * /remove:g only drops the explicit Everyone grant cacls just re-added.
 * Never prints file contents; icacls /Q + >nul keep the log to status lines.
 * Does not /reset or change inheritance on anything below the path, so a file
 * with its own protected ACL (e.g. the guestbook signing key) keeps that ACL;
 * only an Everyone ACE (added by cacls /t) is removed from it.
 */
private String _protectBat(List protectPaths) {
    def out = []
    for (int i = 0; i < protectPaths.size(); i++) {
        def p = protectPaths[i]
        out << """
        rem --- protectPaths: strip Everyone (S-1-1-0) from ${p} ---
        set "PROTECT=%TARGET%\\${p}"
        if exist "%PROTECT%" (
          icacls "%PROTECT%" /inheritance:d /Q >nul
          if errorlevel 1 (
            echo protectPaths: icacls /inheritance:d failed on ${p}
            exit /b 1
          )
          icacls "%PROTECT%" /remove:g *S-1-1-0 /Q >nul
          if errorlevel 1 (
            echo protectPaths: icacls /remove:g Everyone failed on ${p}
            exit /b 1
          )
          icacls "%PROTECT%" /remove:g *S-1-1-0 /T /C /Q >nul
          if errorlevel 1 echo WARNING: protectPaths: icacls reported errors below ${p} - verifying
          icacls "%PROTECT%" /T /C 2>nul | findstr /I /C:"Everyone:" >nul
          if not errorlevel 1 (
            echo protectPaths: Everyone ACE still present under ${p} - failing build
            exit /b 1
          )
          echo protectPaths: Everyone removed from ${p}
        ) else (
          echo protectPaths: ${p} not present on target - skipped
        )"""
    }
    return out.join('\n')
}

/** Optional list of simple names (dirs, or files when allowWild) — rejects shell/cmd metacharacters. */
private List _nameList(def v, String argName, boolean allowWild) {
    if (v == null) {
        return []
    }
    if (!(v instanceof List)) {
        error "deploySite: ${argName} must be a List of names"
    }
    def out = []
    for (def item : v) {
        def n = item?.toString()?.trim()
        if (!n) {
            continue
        }
        if (!_isSafeName(n, allowWild)) {
            error "deploySite: ${argName} entry '${n}' must be a plain name (letters, digits, . _ -${allowWild ? ' * ?' : ''})"
        }
        out << n
    }
    return out
}

/** Optional list of relative target subpaths; normalised to backslashes, no '..', no absolute paths. */
private List _protectList(def v) {
    if (v == null) {
        return []
    }
    if (!(v instanceof List)) {
        error 'deploySite: protectPaths must be a List of relative paths (e.g. [\'App_Data\'])'
    }
    def out = []
    for (def item : v) {
        def raw = item?.toString()?.trim()
        if (!raw) {
            continue
        }
        def p = _normRelPath(raw)
        if (!p) {
            error "deploySite: protectPaths entry '${raw}' must be a relative path under iisPath (letters, digits, . _ - and \\ or /; no '..')"
        }
        out << p
    }
    return out.unique()
}

@NonCPS
private boolean _isSafeName(String n, boolean allowWild) {
    return allowWild ? (n ==~ '[A-Za-z0-9_.*?-]+') : (n ==~ '[A-Za-z0-9_.-]+')
}

@NonCPS
private String _normRelPath(String raw) {
    String p = raw.replace('/', '\\')
    while (p.endsWith('\\')) {
        p = p.substring(0, p.length() - 1)
    }
    if (!(p ==~ '[A-Za-z0-9_. \\\\-]+')) {
        return null
    }
    if (p.startsWith('\\')) {
        return null
    }
    for (String seg : p.split('\\\\', -1)) {
        if (seg == '' || seg == '.' || seg == '..' || seg.trim() != seg) {
            return null
        }
    }
    return p
}
