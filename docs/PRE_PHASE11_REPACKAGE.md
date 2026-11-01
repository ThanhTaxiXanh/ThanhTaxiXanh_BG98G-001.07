# Pre-Phase-11 package refactor and application identity

## Requested identity

- App label: **TTX AALauncher**
- Exact Android application ID: `vn.thanhtaxixanh.bg98g00107` (case-sensitive)
- Gradle namespace: `vn.thanhtaxixanh.bg98g00107`
- Kotlin packages: `vn.thanhtaxixanh.bg98g00107.car`, `.dashboard`, `.ui`,
  `.map`, `.gps`, `.road`, `.signs`, `.calendar`,
  `.reports`, `.qr`, `.media`, `.core`
- Source path: `app/src/main/java/vn/thanhtaxixanh/bg98g00107/`
- Unit tests: `app/src/test/java/vn/thanhtaxixanh/bg98g00107/`
- Artifact: `TTX-AALauncher-debug`

## Backward-compatibility warning (critical)

Changing `applicationId` from `com.cartaxi.launcher` is **not** an
in-place rename or an upgrade. Android treats this as a separate package
with a separate UID and private data sandbox. The old app may coexist
with TTX AALauncher. Local preferences, QR originals and mirrors,
traffic reports, event notifications, media URIs and offline road/sign
cache cannot be read by the new app simply by renaming the package.
Signing with the same key **does not** automatically move private data.

Do not uninstall the previous application until any needed personal
events, QR and report data have been exported/saved via user-authorized
means. Full legacy calendar/settings migration is not implemented by
this refactor. Use no filesystem permission or automatic access to
the previous application's sandbox.

Existing `KEYTHANHTAXIXANH` and certificate names in CI refer to
historical signing configuration. Changing application ID does not
require the original signing key for side-by-side installation, but a
published TTX AALauncher app should consistently use an approved
release signer for future updates.

## Refactor scope and checks

- Moved production and JVM Kotlin sources from
  `com/cartaxi/launcher` into functional packages under the new
  namespace, with explicit import dependencies.
- Rewired component class names in AndroidManifest to new packages.
- Kept runtime workflows for QR/events/reports and map feature logic
  unchanged; the new app starts with new private storage.
- CI enforces both new APK package identity and launcher label with
  Android SDK `aapt` (in addition to build, unit tests and artifact).
- GitHub repository name remains AALauncherGPT and its historical audit
  references are not renamed, to preserve previous commit/release links.
- No code from legacy package is bundled inside TTX AALauncher.

## Phase 11 gates remain

Android Auto navigation template compliance (NF-1/NF-2),
instrumentation, real host tests, signed release and on-device stability
still require independent validation. GPS matcher/road-graph CPU/RAM
profiling remains deferred as specifically requested.
