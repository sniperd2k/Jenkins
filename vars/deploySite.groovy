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
 *              Windows, per path: [optional appPoolModify grant], then
 *              icacls /inheritance:d (inherited ACEs copied as explicit, so
 *              AppPool/SYSTEM/Administrators keep access), then icacls
 *              /remove:g *S-1-1-0 (Everyone) on the path, then /T /C over the
 *              subtree, then a PowerShell Get-Acl check by SID (language-
 *              independent) that no Everyone ACE remains. Fails the build if the
 *              grant or icacls errors on the path itself, or Everyone remains.
 *              Unix: no-op (the rsync promote does not loosen permissions).
 *   appPoolModify — IIS app pool name (e.g. 'davidunderwood.net'; [A-Za-z0-9._-]).
 *              Requires protectPaths. Before each Everyone strip, runs
 *              icacls "<path>" /grant "IIS AppPool\<name>:(OI)(CI)M" (idempotent:
 *              re-granting an identical ACE is a no-op). If the grant fails, the
 *              build fails and the strip never runs.
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
    def appPoolModify = _appPoolName(args.appPoolModify)
    if (appPoolModify) {
        if (!protectPaths) {
            error 'deploySite: appPoolModify only applies with protectPaths (grant is made on each protected path)'
        }
        echo "deploySite: appPoolModify '${appPoolModify}' (Modify granted on protectPaths before Everyone strip)"
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
    _promote(iisPath, excludes, excludeFiles, protectPaths, appPoolModify)

    echo "=== deploySite done: ${siteName} → ${iisPath} ==="
}

private void _promote(String iisPath, List excludes, List excludeFiles, List protectPaths, String appPoolModify) {
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
    def protectBlock = protectPaths ? _protectBat(protectPaths, appPoolModify) : ''
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
 * Order per path: [grant app pool Modify] -> /inheritance:d -> strip Everyone on
 * the path -> strip Everyone below it -> verify by SID. Every step that can leave
 * the path open or the app pool without access exits the bat (build fails)
 * before the next one runs.
 * Idempotent: /grant of an identical ACE, /inheritance:d once already off, and
 * /remove:g of an absent ACE are all no-ops.
 * Never prints file contents; icacls /Q + >nul and the verify script only print
 * status lines / a count.
 * Does not /reset or change inheritance on anything below the path, so a file
 * with its own protected ACL (e.g. the guestbook signing key) keeps that ACL;
 * only an Everyone ACE (added by cacls /t) is removed from it.
 */
private String _protectBat(List protectPaths, String appPoolModify) {
    // Language-independent verify: count ACEs whose SID is S-1-1-0 (Everyone) on the
    // path and every item below it. Unreadable items throw -> exit 1 (fail closed).
    // Single-quoted Groovy string: no interpolation; contains no double quotes / % for cmd.
    def verifyPs = '$ErrorActionPreference=\'Stop\'; ' +
        '$sid=New-Object System.Security.Principal.SecurityIdentifier(\'S-1-1-0\'); ' +
        '$t=[System.Security.Principal.SecurityIdentifier]; ' +
        '$items=@(Get-Item -LiteralPath $env:PROTECT -Force) + @(Get-ChildItem -LiteralPath $env:PROTECT -Recurse -Force); ' +
        '$n=0; foreach($i in $items){ foreach($r in (Get-Acl -LiteralPath $i.FullName).GetAccessRules($true,$true,$t)){ if($r.IdentityReference -eq $sid){ $n++ } } }; ' +
        'if($n -gt 0){ Write-Host (\'protectPaths: Everyone ACEs remaining: \' + $n); exit 1 }; exit 0'
    def out = []
    for (int i = 0; i < protectPaths.size(); i++) {
        def p = protectPaths[i]
        def grant = ''
        if (appPoolModify) {
            grant = """
          icacls "%PROTECT%" /grant "IIS AppPool\\${appPoolModify}:(OI)(CI)M" /Q >nul
          if errorlevel 1 (
            echo protectPaths: icacls /grant app pool Modify failed on ${p} - Everyone NOT stripped
            exit /b 1
          )"""
        }
        out << """
        rem --- protectPaths: strip Everyone (S-1-1-0) from ${p} ---
        set "PROTECT=%TARGET%\\${p}"
        if exist "%PROTECT%" (${grant}
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
          "%SystemRoot%\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "${verifyPs}"
          if errorlevel 1 (
            echo protectPaths: Everyone S-1-1-0 still present under ${p} or ACLs unreadable - failing build
            exit /b 1
          )
          echo protectPaths: Everyone removed from ${p}
        ) else (
          echo protectPaths: ${p} not present on target - skipped
        )"""
    }
    return out.join('\n')
}

/** Optional IIS app pool name for appPoolModify: [A-Za-z0-9._-], not all dots, no trailing dot. */
private String _appPoolName(def v) {
    if (v == null) {
        return null
    }
    def n = v.toString().trim()
    if (!n) {
        return null
    }
    if (!(n ==~ '[A-Za-z0-9._-]+') || !_isSaneSegment(n)) {
        error "deploySite: appPoolModify '${n}' must be an app pool name ([A-Za-z0-9._-], not all dots, no trailing dot)"
    }
    return n
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
            error "deploySite: ${argName} entry '${n}' must be a plain name (letters, digits, . _ -${allowWild ? ' * ?' : ''}; not all dots${allowWild ? '/wildcards' : ''}; no trailing dot)"
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
            error "deploySite: protectPaths entry '${raw}' must be a relative path under iisPath (letters, digits, . _ - and \\ or /; no all-dot or trailing-dot segments)"
        }
        out << p
    }
    return out.unique()
}

@NonCPS
private boolean _isSafeName(String n, boolean allowWild) {
    if (!(allowWild ? (n ==~ '[A-Za-z0-9_.*?-]+') : (n ==~ '[A-Za-z0-9_.-]+'))) {
        return false
    }
    // '.', '..', '*', '*.*', '?' etc. would match everything / nothing useful
    if (n ==~ '[.*?]+') {
        return false
    }
    return _isSaneSegment(n)
}

/** Rejects segments that are all dots or end with a dot (Win32 strips trailing dots). */
@NonCPS
private boolean _isSaneSegment(String seg) {
    return !(seg ==~ '\\.+') && !seg.endsWith('.')
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
        if (seg == '' || seg.trim() != seg || !_isSaneSegment(seg)) {
            return null
        }
    }
    return p
}
