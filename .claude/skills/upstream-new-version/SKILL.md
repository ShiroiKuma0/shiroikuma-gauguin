---
name: upstream-new-version
description: Rebase this fork onto a new upstream release of meikpiep/gauguin. Use when 白い熊 says a new upstream version is out, asks to update/sync to upstream, bump to the new Gauguin release, or rebase custom onto the latest upstream tag — then build the new +1. ALWAYS present the proceed-gated upstream-changes table BEFORE rebasing.
---

# Sync shiroikuma-gauguin onto a new upstream Gauguin release

This fork tracks [meikpiep/gauguin](https://github.com/meikpiep/gauguin) — a Sudoku-like calculation
puzzle (KenKen / Killer-Sudoku family), pure Kotlin/Android, no native code. We follow upstream's
**tagged releases** (`v0.52.0`, `v0.53.0`, …), not the `main` tip.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors the latest upstream **release tag**. No fork work here. | fast-forward / reset to the tag |
| `custom` | Our patches; the working/dev branch and the GitHub default branch. | rebased onto `master` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-gauguin` (push). `upstream` =
`https://github.com/meikpiep/gauguin` (fetch only; its push URL is set to `DISABLED`).

Upstream's own default branch is `main`; our mirror branch is deliberately named `master`.

## Steps

1. **Fetch upstream and find the new release:**
   ```bash
   git fetch upstream --tags
   git tag --sort=-creatordate | head -5          # newest upstream tags
   git describe --tags --exact-match master       # the tag we are currently based on
   ```
   Read the new base version out of upstream's manifest — this fork takes its version from there,
   not from a gradle property:
   ```bash
   git show <newtag>:gauguin-app/src/main/AndroidManifest.xml | grep -E 'android:version(Code|Name)'
   ```

2. **PROCEED GATE — present the upstream changes as a table, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream version actually brings.

   Gather the material from both sources — they complement each other:
   ```bash
   git show <newtag>:CHANGELOG.md | head -120                     # upstream's own release notes
   git log --oneline --no-merges <oldtag>..<newtag>               # what really landed
   git diff --stat <oldtag>..<newtag>                             # where the weight is
   ls fastlane/metadata/android/en-US/changelogs/                 # per-versionCode store notes
   ```

   Render **one markdown table**, ordered most-significant first, in this exact shape:

   | # | Change | Kind | What it means in the app | Touches our patches? |
   | --- | --- | --- | --- | --- |
   | 1 | … | Feature / Fix / UI / Perf / Refactor / Dependency | one clear sentence, in plain terms | No — or: yes, `<file>` (our icon / label / version block …) |

   Rules for the table:
   - **Every** notable upstream change gets a row — do not summarise into "various fixes". Group only
     genuinely trivial churn (typo fixes, translation drops, dependency version bumps) into a single
     final row, and say how many were folded in.
   - The **last column is the point**: flag every change that lands in a file we patch — the launcher
     icon resources, `values/strings.xml` (`app_name`), `gauguin-app/build.gradle.kts`, the manifest,
     the About/Help screens, or anything carrying upstream branding we stripped. Those are the rebase
     conflicts, predicted in advance.
   - Below the table, add the base line: old tag → new tag, old `versionCode`/`versionName` → new,
     and the resulting fork version (`<newVersionName>+001`, code `<newVersionCode> * 10000 + 1`).

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not fetch-forward `master`, do not
   rebase, do not build until they say proceed. If they decline, nothing has been touched.

3. **Advance `master` to the new tag** (mirror; no fork work lives here):
   ```bash
   git checkout master
   git merge --ff-only <newtag>      # or: git reset --hard <newtag>
   git push origin master
   ```

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase master
   ```
   Resolve conflicts so **all** our customizations survive (table in step 6). The new upstream
   `android:versionCode` / `android:versionName` in `gauguin-app/src/main/AndroidManifest.xml` flow in
   automatically — keep **upstream's** values for those two attributes. Our fork block reads them out
   of the manifest and derives the fork version from them, so they are never edited by hand.

5. **Reset the build tail:** in `gradle.properties`, set **`BUILD_NUMBER=1`** — a new upstream line
   starts its `+N` at 1.

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.gauguin` | `gauguin-app/build.gradle.kts` → `defaultConfig.applicationId` |
   | Code namespace | `org.piepmeyer.gauguin` (**unchanged** from upstream) | `gauguin-app/build.gradle.kts` → `namespace` |
   | App label | `白い熊 GNU Gauguin` | `app_name` in `gauguin-app/src/main/res/values/strings.xml` |
   | Fork version block | manifest-derived base + `forkVersionName` / `forkVersionCode` | `gauguin-app/build.gradle.kts` |
   | `buildFork` task | present, copies to `~/tmp`, bumps `BUILD_NUMBER` | `gauguin-app/build.gradle.kts` |
   | Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
   | Signing | `keystore.properties` at the repo root (gitignored) → `~/.android-keystores/shiroikuma-gauguin.jks`, alias `gauguin` | upstream's own `keystoreExists` block |
   | Black-yellow icon | yellow `#FFFF00` traced line-art, black `ic_launcher_background` | `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`, `mipmap-*/ic_launcher*.png`, `src/main/ic_launcher-playstore.webp` |
   | De-branding | no upstream app name / GitHub / issue / donate links left in user-visible text | About + Help screens, `values*/strings.xml`, `res/xml/*` |
   | Committed agent files | `CLAUDE.md`, `.claude/` un-ignored; signing material ignored | `.gitignore` |

   Conflict-prone files: `gauguin-app/build.gradle.kts`, `gradle.properties`, `values/strings.xml`,
   the launcher icon resources, and any About/Help resource we de-branded. If upstream restructured a
   screen we patched, port our change to the new structure rather than forcing the old diff.

   Sanity check that the build script still evaluates:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :gauguin-app:assembleRelease --dry-run
   ```

7. **Build the new `+001`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null`), then deliver it
   via the global **/after-build** skill (no transfer prompt). This is the first build of the new
   upstream line.

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**. `custom` was rebased, so
   it needs `git push --force-with-lease origin custom`; `master` is a plain fast-forward.

## Hard rules
- **Never rebase before the step-2 table has been shown and approved.**
- Never `adb install` / `adb uninstall` — 白い熊 installs manually from `/sdcard/tmp/`.
- Never commit/push unprompted; wait for "Push".
- `keystore.properties` and `*.jks` are gitignored — never commit them.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
