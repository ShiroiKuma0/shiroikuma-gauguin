---
name: build-apk
description: Build the signed release APK of 白い熊 GNU Gauguin with the buildFork Gradle task, then deliver it automatically via the global /after-build skill. Build PROACTIVELY as soon as a coherent code change compiles — do NOT wait for 白い熊 to say "build it". Also use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the release APK and deliver it

This is **shiroikuma-gauguin** — 白い熊's fork of [Gauguin](https://github.com/meikpiep/gauguin),
renamed to `shiroikuma.gauguin` ("白い熊 GNU Gauguin") so it installs side-by-side with upstream.
Pure Kotlin/Android, view binding + Koin, no native code — so the APK is universal, no ABI suffix.

## When to build

Build **proactively** — do NOT wait for "build it" and do NOT ask "want me to build?" first. As soon
as a coherent set of code changes compiles, run the steps below. Don't rebuild after every tiny
intermediate edit — build once the change is in a testable state. Skip the build for non-functional
edits (docs, comments).

This removes only the *ask-before-build* wait. The repo's commit/push rules are unchanged: a
commit/push still waits for 白い熊's explicit **"Push"**.

## Steps

1. **Note the output filename.** The version base comes from upstream's manifest, the tail from
   `gradle.properties`:
   ```bash
   grep -E 'android:version(Code|Name)' gauguin-app/src/main/AndroidManifest.xml
   grep -E '^BUILD_NUMBER' gradle.properties      # the N used for THIS build, before the task bumps it
   ```
   - APK will be `shiroikuma-gauguin_<versionName>+<NNN>.apk`, the counter **zero-padded to three
     digits** — `BUILD_NUMBER=7` → `0.52.0+007`. The padding is applied in
     `gauguin-app/build.gradle.kts`; `gradle.properties` stores the plain integer.
   - `versionCode` for this build = `<upstream versionCode> * 10000 + BUILD_NUMBER`
     (e.g. `76 * 10000 + 7 = 760007`).

2. **Build** (the toolchain needs JDK 21 — the host default `java` is too old for AGP 9.x):
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null
   ```
   (`< /dev/null` guarantees it never blocks on stdin.)
   - `buildFork` runs `assembleRelease` (signed from `keystore.properties`), copies the signed APK to
     `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>`. Confirm `BUILD SUCCESSFUL` and take the exact
     filename/code from those lines.
   - If it fails with **`SDK location not found`**, create the gitignored `local.properties` at the
     repo root with `sdk.dir=/home/shiroikuma/android-sdk` (a background shell doesn't inherit
     `ANDROID_HOME`).
   - If the APK comes out unsigned, `keystore.properties` is missing from the repo root — see below.

3. **Deliver automatically via the global /after-build skill** — every build, no asking. After the
   signed APK is in `~/tmp/`, invoke **/after-build**: it runs `/adb-check` UNSANDBOXED (a sandboxed
   check falsely reports no device), then `/adb-push` to `/sdcard/tmp/` if a phone is connected,
   otherwise `/scp` to `skhw:~/tmp/`, and announces the filename. Deliver to exactly ONE target.

## Signing

Release signing is non-interactive and uses **upstream's own** `keystoreExists` block — it reads a
`keystore.properties` at the repo root (gitignored):

```
storePassword=…
keyPassword=…
keyAlias=gauguin
storeFile=/home/shiroikuma/.android-keystores/shiroikuma-gauguin.jks
```

The keystore is PKCS12/RSA-4096, alias `gauguin`, created 2026-08-02. Its password is recorded in
`~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, and the `.jks` is backed up to
`~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`. If `keystore.properties` is absent the build
still succeeds but the APK is **unsigned** and will not install.

## Notes / invariants

- **Toolchain:** JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`; Android SDK at `~/android-sdk`;
  `compileSdk android-37`, `buildToolsVersion 37.0.0`, `targetSdk 37`, `minSdk 24`; Gradle wrapper
  9.3.1, AGP 9.1.1.
- **No minification** — upstream ships `isMinifyEnabled = false` for release; leave it that way
  unless 白い熊 asks otherwise.
- **Universal APK** — no ABI splits, no native libs, so no `_arm64-v8a` tail in the filename.
- **Upstream's other Gradle entry points** (`sonar`, `roborazzi` screenshot tests, the
  `micro-benchmark` module, the fastlane/Play plugin) are upstream's CI machinery — we don't ship or
  run them as part of a build.
- **Never commit/push on your own.** Wait for 白い熊's explicit "Push". Build artifacts (`*.apk`),
  `keystore.properties`, `*.jks` and `local.properties` are gitignored.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
