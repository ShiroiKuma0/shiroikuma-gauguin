# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-gauguin** — 白い熊's fork of [Gauguin](https://github.com/meikpiep/gauguin), a FOSS
Android puzzle game in the KenKen / Killer-Sudoku family: you fill a grid so that each cage satisfies
an arithmetic constraint. Pure Kotlin/Android (view binding, Koin DI, Material 3), no native code.
Renamed to `shiroikuma.gauguin` so it installs side-by-side with upstream.

This repo (`ShiroiKuma0/shiroikuma-gauguin`) is a fork. We track upstream (`meikpiep/gauguin`) on
`master` and layer our customizations on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table). Publishing a release uses the **global** `/publish-version`
skill — this repo has no local copy.

## Fork workflow — READ THIS FIRST

### Git remotes & branches
- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-gauguin` (push here).
- `upstream` → `https://github.com/meikpiep/gauguin` (fetch only; push URL set to `DISABLED`).
- `master` — mirrors the latest upstream **release tag**, no fork work. Upstream's own default branch
  is `main`; our mirror is deliberately called `master`.
- `custom` — all our work, and the GitHub default branch so the repo page lands on the fork.

**Upstream tracking: releases.** We sync when upstream tags a release (`v0.52.0`, `v0.53.0`, …), not
on every push to `main`. The global `/git-versioning` skill therefore does **not** apply here.

### Our customizations (install identity + build)
| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.gauguin` | `gauguin-app/build.gradle.kts` → `defaultConfig` |
| namespace (R/BuildConfig pkg) | `org.piepmeyer.gauguin` (**never rename**) | `gauguin-app/build.gradle.kts` |
| App label | `白い熊 GNU Gauguin` | `app_name` in `gauguin-app/src/main/res/values/strings.xml` |
| App icon | black-yellow traced mark (yellow `#FFFF00` line-art on black) | `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`, `mipmap-*/ic_launcher*.png`, `src/main/ic_launcher-playstore.webp` |
| Version tail | `versionName = "<base>+NNN"`, `versionCode = <baseCode>*10000+N` | `gauguin-app/build.gradle.kts` fork block |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-gauguin.jks` (alias `gauguin`) | upstream's own `keystoreExists` block |
| De-branding | our name + our GitHub links everywhere user-visible | About / Help screens, `values*/strings.xml`, `ui/main/MainNavigationViewService.kt` |

### Versioning & APK naming
- **The upstream base lives in the manifest**, not in a gradle property:
  `gauguin-app/src/main/AndroidManifest.xml` → `android:versionCode` / `android:versionName`
  (currently `76` / `0.52.0`). Our fork block **reads it from there**, so on every upstream rebase the
  new base flows in automatically and is never edited by hand.
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<base>+<N zero-padded to 3>"` (e.g. `0.52.0+001`),
  `versionCode = <baseCode> * 10000 + N` (plain integer — the padding is text only, e.g. `760001`).
  The `buildFork` task bumps `BUILD_NUMBER` after every successful build; the upstream-sync skill
  resets it to `1`.
- APK: `shiroikuma-gauguin_<versionName>.apk`, copied to `~/tmp/`. **No ABI suffix** — the app has no
  native code, so the APK is universal.

### Build commands
```bash
# Our build: signed release → ~/tmp + bump BUILD_NUMBER (use this)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null
# Release APK only (no copy / no bump)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :gauguin-app:assembleRelease
```

### Toolchain
- JDK **21** at `/usr/lib/jvm/java-21-openjdk-amd64` (the host default `java` is older; AGP 9.x
  aborts on it — always set `JAVA_HOME`).
- Android SDK at `~/android-sdk`; `compileSdk android-37`, `buildToolsVersion 37.0.0`,
  `targetSdk 37`, `minSdk 24`. Gradle wrapper 9.3.1, AGP 9.1.1.
- Release is **not** minified upstream (`isMinifyEnabled = false`) — leave it that way unless asked.

## Architecture (upstream Gauguin)

Multi-module Gradle build; `settings.gradle.kts` includes:

| Module | Role |
| --- | --- |
| `gauguin-app` | the Android app — activities, view binding layouts, preferences, Koin `AppModule` |
| `gauguin-core` | game model and logic — `grid/`, `game/`, `creation/`, `calculation/`, `difficulty/`, `statistics/`, `history/`, `undo/` |
| `gauguin-human-solver` | the human-style solver used to rate a grid's difficulty |
| `gauguin-grid-creation-via-merge` | an alternative grid generator |
| `micro-benchmark` | upstream's JMH-style benchmarks (CI only) |

App entry points: `MainApplication` (Koin start), `ui/main/MainActivity`, plus `NewGameActivity`,
`ChooseChallengeActivity`, `LoadGameListActivity`, `StatisticsActivity`, `SettingsActivity`,
`AboutActivity`. Preferences are backed by `res/xml/root_preferences.xml`.

Upstream's CI machinery — `sonarqube`, Roborazzi screenshot tests, fastlane / Play publishing, the
`micro-benchmark` module — is upstream's; we neither ship nor run it as part of a build.

## Hard rules
- **Always run `adb` with `dangerouslyDisableSandbox: true`** (the sandbox blocks adb's server
  socket, so `adb devices` shows empty). Every `adb` invocation goes through the unsandboxed path.
  The repo itself lives under `~/git/`, which is outside the sandbox write allowlist — git and build
  commands here also need the unsandboxed path.
- **Never `adb install` / `adb uninstall`** — 白い熊 installs manually from `/sdcard/tmp/`.
- **Build proactively, deliver once.** After any functional change, build via the `build-apk` skill
  and deliver via the global `/after-build` skill — no asking. Delivery goes to exactly ONE target.
- **Never commit/push unprompted.** Wait for 白い熊's explicit "Push". `custom` is rebased on every
  upstream sync, so it pushes with `git push --force-with-lease origin custom`.
- **`/upstream-new-version` must show the proceed-gated upstream-changes table before rebasing.**
  This is a standing requirement, not a nicety — see the skill.
- `keystore.properties`, `*.jks` and `local.properties` are gitignored — never commit them.
- **Never rename the `namespace`** (`org.piepmeyer.gauguin`). Only `applicationId` changes; leading-dot
  class names in the manifest resolve against `applicationId`, so keep manifest entries consistent
  with what upstream ships.

## History

**Fork bootstrapped** (2026-08-02): forked from `meikpiep/gauguin` at `v0.52.0` (versionCode 76),
`master`/`custom` branch model, own keystore, fork versioning + signing, the `buildFork` task, the
black-yellow traced icon, de-branding, and the two repo skills.

## Commit convention — no Claude attribution
Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the last
line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
