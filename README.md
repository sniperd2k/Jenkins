# sniperd2k/Jenkins — shared pipeline library

Central Jenkins **Global Pipeline Library** for sniperd2k site deploys.

Suggested Jenkins library name: **`sniperd-jenkins`**

```groovy
@Library('sniperd-jenkins') _
```

This repo owns pipeline content only. **Do not** change Jenkins controller config from automation — Ops loads Global Pipeline Libraries manually.

## Branches

| Branch | Role |
|--------|------|
| `master` | production library tip (default) |
| `beta`   | pre-prod library tip (same pattern as other sniperd2k sites) |

## How Ops loads the Global Shared Library

1. Jenkins → **Manage Jenkins** → **System** → **Global Pipeline Libraries**
2. **Add** → Name: `sniperd-jenkins`
3. Default version: `master` (or `beta` for soak)
4. Retrieval method: **Modern SCM** → Git
5. Project repository: `https://github.com/sniperd2k/Jenkins.git`
6. Credentials: **none required** (public HTTPS). If your controller still expects a credential, use the existing GitHub credential (see placeholders below).
7. Library path: *(leave blank — `vars/` at repo root)*
8. Check **Load implicitly** only if you want every job to see it without `@Library`; otherwise jobs use `@Library('sniperd-jenkins') _`
9. Point each site job at `jenkinsfiles/<site>.Jenkinsfile` in this repo **or** paste that thin file as the job’s Pipeline script / SCM path.

Webhook: GitHub repo webhooks point at the controller's standard endpoint, `<jenkins-host>/github-webhook/`. The real host and port are kept out of this public repo and live in Ops' private notes.

## Public vars

### `runTests`

Default pre-**FileCopy** gate (sniperd pattern: **red blocks deploy**).

| Condition | Behavior |
|-----------|----------|
| `package.json` has `test:gate` | `npm run test:gate` (preferred) |
| else has `test:all` | `npm run test:all` |
| else has `test` | `npm test` |
| `package.json` but none of those scripts | **warn**, continue |
| no `package.json` | **warn**, continue (static HTML) |
| chosen script exits non-zero | **fail build** → blocks FileCopy |

Uses `withNode` so PATH is pinned to `C:\grok\tools\node`.

### `deploySite`

Checkout (optional) → `runTests` → FileCopy-style promote (robocopy on Windows).

```groovy
deploySite(
    repo: 'sniperd2k/sniperd.com',
    iisPath: 'F:\\website\\sniperd.com',
    siteName: 'sniperd.com',
    credentialsId: 'github-sniperd2k',  // placeholder — see below
    branch: 'master'                     // optional; default BRANCH_NAME or master
)
```

- **Preserves** existing `App_Data\*.json` on the IIS target (guestbook / ASP.NET state).
- Excludes `node_modules`, `.git`, `tests`, `e2e`, `test-results`, etc.
- Sniperd gate map: `test:gate` (= vitest + playwright) **must** pass before promote.

#### Optional promote args (opt-in; omit them and the promote is unchanged)

| Arg | Type | Effect |
|-----|------|--------|
| `excludes` | List of dir names | **Replaces** the default excludes (`node_modules`, `.git`, `.github`, `tests`, `e2e`, `test-results`, `playwright-report`, `coverage`, `.vscode`, `.idea`). |
| `extraExcludes` | List of dir names | **Appended** to `excludes` / the defaults. robocopy `/XD` + rsync `--exclude`; matches that directory name at any depth. |
| `excludeFiles` | List of file names / wildcards | **Appended** to robocopy `/XF .gitignore .gitattributes` and rsync `--exclude`. Excluded files are never copied, so a live copy on the target is never overwritten (the promote has no `/MIR` / `--delete`, so nothing on the target is deleted either). |
| `protectPaths` | List of paths relative to `iisPath` | Strips **Everyone** from those paths *after* the legacy `cacls "%TARGET%" /t /e /g Everyone:f` step (Windows only; the Unix rsync promote never loosens permissions, so it is a no-op there). |
| `appPoolModify` | String: IIS app pool name (`[A-Za-z0-9._-]`) | Requires `protectPaths`. Before each Everyone strip, grants `IIS AppPool\<name>:(OI)(CI)M` on the path, so the site keeps write access once Everyone is gone. |

Validation (build fails on a bad value): names may only use letters, digits, `.`, `_` and `-` (plus `*` / `?` in `excludeFiles`). Names made only of dots or wildcards (`.`, `..`, `*`, `*.*`) and names ending in a dot are rejected. `protectPaths` must be relative, with no drive or leading `\`, no empty segments, and no segment that is all dots or ends in a dot.

`protectPaths` details, per path, run after `cacls` on every deploy (idempotent):

0. *(only with `appPoolModify`)* `icacls "<path>" /grant "IIS AppPool\<name>:(OI)(CI)M"`: re-granting an identical ACE does nothing, so this is safe when Ops has already added it. If it fails, the build fails and **nothing below runs**, so Everyone is never stripped without the pool's grant in place.
1. `icacls "<path>" /inheritance:d` — stops inheriting from the site root (where `cacls /t` leaves an inheritable `Everyone:F`). The inherited ACEs are copied as explicit, so the IIS AppPool identity, SYSTEM and Administrators keep their access. No-op once inheritance is already off.
2. `icacls "<path>" /remove:g *S-1-1-0` — removes every Everyone (SID `S-1-1-0`, locale-independent) grant from the path itself.
3. `icacls "<path>" /remove:g *S-1-1-0 /T /C` — removes the explicit Everyone grant that `cacls /t` re-added to every child.
4. Verifies, independent of OS language, that no ACE for SID `S-1-1-0` remains on the path or anything below it. It uses PowerShell `Get-Acl` with `GetAccessRules(..., [SecurityIdentifier])`. An item whose ACL can't be read also fails the check (fail closed). Only a count is printed.

The build **fails** if step 0, 1 or 2 errors on the path itself, or if step 4 finds Everyone or can't read an ACL. Errors on individual children in step 3 only warn, and step 4 then decides. Nothing below the path gets `/reset` or an inheritance change, so a file with its own protected ACL (e.g. a signing key) keeps it; only the Everyone ACE that `cacls /t` added is removed. A path missing on the target is skipped. Output is suppressed (`/Q`, `>nul`), so no file names or contents are echoed.

Example (`jenkinsfiles/davidunderwood.net.Jenkinsfile`, SEC-1):

```groovy
deploySite(
    repo: 'sniperd2k/davidunderwood.net',
    iisPath: 'F:\\website\\davidunderwood.net',
    siteName: 'davidunderwood.net',
    extraExcludes: ['scripts'],                // ops scripts never reach IIS
    excludeFiles: ['guestbook-signing-key*'],  // live key is server-only; never copied/overwritten
    protectPaths: ['App_Data'],                // undo cacls Everyone:f on App_Data
    appPoolModify: 'davidunderwood.net'        // pool keeps Modify on App_Data without Everyone
)
```

> Note: `cacls /t` still briefly grants Everyone on protected paths during each deploy, until the `icacls` steps above run. Dropping the `cacls` step for a site would be a separate change.

### `withNode`

Pins `NODE_HOME` / `PATH` to **`C:\grok\tools\node`** (v**22.19.0** on the Windows build agent).  
Also sets `PLAYWRIGHT_BROWSERS_PATH=C:\grok\tools\playwright-browsers`.

## Sample Jenkinsfile pointers

| Kind | Path | Notes |
|------|------|-------|
| Static site | `jenkinsfiles/amandaunderwood.com.Jenkinsfile` | no `package.json` → warn + continue, then FileCopy |
| Sniperd (gated) | `jenkinsfiles/sniperd.com.Jenkinsfile` | `test:gate` before FileCopy |
| ASP.NET / App_Data | `jenkinsfiles/davidunderwood.net.Jenkinsfile` | preserves `App_Data\*.json`; `extraExcludes` / `excludeFiles` / `protectPaths` / `appPoolModify` (SEC-1) |

Every site file is thin: `@Library` + one `deploySite(...)` call.

## Site map (Jenkinsfile → git → IIS)

| Jenkinsfile | GitHub repo | IIS path |
|-------------|-------------|----------|
| `jenkinsfiles/davidunderwood.net.Jenkinsfile` | sniperd2k/davidunderwood.net | `F:\website\davidunderwood.net` |
| `jenkinsfiles/amandaunderwood.com.Jenkinsfile` | sniperd2k/amandaunderwood.com | `F:\website\amandaunderwood.com` |
| `jenkinsfiles/sniperd.com.Jenkinsfile` | sniperd2k/sniperd.com | `F:\website\sniperd.com` |
| `jenkinsfiles/avaunderwood.com.Jenkinsfile` | sniperd2k/avaunderwood.com | `F:\website\avaunderwood.com` |
| `jenkinsfiles/emmeunderwood.com.Jenkinsfile` | sniperd2k/emmeunderwood.com | `F:\website\emmeunderwood.com` |
| `jenkinsfiles/moodybeachmaine.com.Jenkinsfile` | sniperd2k/moodybeachmaine.com | `F:\website\moodybeachmaine.com` |
| `jenkinsfiles/saynotoskiing.com.Jenkinsfile` | sniperd2k/saynotoskiing.com | `F:\website\saynotoskiing.com` |
| `jenkinsfiles/evilgame.com.Jenkinsfile` | sniperd2k/evilgame.com | `F:\website\evilgame.com` |
| `jenkinsfiles/drunkliar.com.Jenkinsfile` | sniperd2k/drunkliar.com | `F:\website\drunkliar.com` |
| `jenkinsfiles/blowdank.com.Jenkinsfile` | sniperd2k/drunkliar.com (shared; no blowdank repo) | `F:\website\blowdank.com` |

**Moody / SayNo:** this repo only ships pipeline Jenkinsfiles — do **not** modify those sites’ content from here.

## Credential ID placeholders

| Placeholder ID | Purpose |
|----------------|---------|
| `github-sniperd2k` | GitHub checkout for every site repo (all site repos are private). Map it in Jenkins to the existing GitHub site-checkout credential: a read-only PAT or GitHub App with access to the site repos. Credential IDs other than this role placeholder are not documented in this public repo. |

**Only this library repo (`sniperd2k/Jenkins`) is public** — Ops can load it over HTTPS with **no credentials**. GitHub secret scanning and push protection are enabled on it; never commit credentials, tokens, or secret values here.

**All 18 other sniperd2k repos, including every site content repo, are private.** Site deploys authenticate their checkout with the `github-sniperd2k` Jenkins credential (`credentialsId: 'github-sniperd2k'`, the `deploySite` default); a checkout without it will fail.

## Node pin

- Path: `C:\grok\tools\node`
- Version: **v22.19.0**
- Playwright browsers: `C:\grok\tools\playwright-browsers`

## Standing rule — GA4 Measurement IDs

Every new/updated site ships the correct GA4 Measurement ID (AllYourBase). Never ship without it.

| Site | Measurement ID |
|------|----------------|
| moodybeachmaine.com | G-CJP2HX87N2 |
| saynotoskiing.com | G-Q2436XH6X2 |
| amandaunderwood.com | G-VSB27XJKDP |
| davidunderwood.net | G-49HF3RFVKP |
| avaunderwood.com | G-W4B6KRME63 |
| emmeunderwood.com | G-GJB1H22DVN |
| sniperd.com | G-3Z1GXBW9W2 |
| drunkliar.com (+ blowdank) | G-F7G1EXTEC6 |
| evilgame.com | G-DPS1Z2QQ99 |

**Do not use:** leftover Amanda `G-Q7ZK1YVHHH` (GA Trash).

## Add a site

1. Confirm GitHub repo `sniperd2k/<site>` exists (`gh repo list sniperd2k`).
2. Add `jenkinsfiles/<site>.Jenkinsfile` calling `deploySite(repo: 'sniperd2k/<site>', iisPath: 'F:\\website\\<site>')`.
3. Ensure site has correct GA4 ID (table above).
4. If the site has tests, prefer a `test:gate` script in `package.json`.
5. PR/push to `master` (and `beta` if soaking). Ask Ops to point the Jenkins job at the new Jenkinsfile — **no controller library rename needed**.

## Self-test

Root `Jenkinsfile` validates README + `vars/` + all site Jenkinsfiles exist and call the library. Wire a job `Jenkins` / `sniperd-jenkins-selftest` against this repo if desired.

## Layout

```
vars/
  deploySite.groovy
  runTests.groovy
  withNode.groovy
src/com/sniperd/jenkins/
  SiteTargets.groovy
jenkinsfiles/
  *.Jenkinsfile          # one per site
Jenkinsfile              # library self-test
README.md
```
