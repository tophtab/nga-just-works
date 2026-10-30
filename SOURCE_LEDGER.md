# Source ledger

## Upstream baseline

- Source: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE>
- Commit: `5d807617f8058950f7ea81dda405e38fb0cc37ec`
- Tree: `12511b22201c3dbfcfb9fd79ac9732b347c47255`
- License: GNU GPL version 2 (`LICENSE`)
- Imported scope: the root Gradle build, all 13 modules declared in
  `settings.gradle`, and upstream GitHub workflows.

This repository is an unofficial modified fork. It is not affiliated with or
endorsed by NGA or the upstream author.

## Fork changes

- Favorite boards use `fid + stid` as their stable identity and retain a
  global, persistent order that can be changed by direct long-press drag.
- Topic lists expose one direct post action and article views expose one direct
  reply action. The expandable floating menu and its refresh action are
  removed; pull-to-refresh remains available, and holding the selected article
  page number refreshes that page periodically until release.
- Embedded release keystore paths and passwords are removed. Signing material
  must be supplied outside version control.
- The legacy official-client user-agent claim and identity header are removed;
  requests use the neutral fork identifier `nga-just-works`.
- Upstream PSD design files are excluded from this fork; their branding rights
  are not established and they are not needed to build the application.
- The pinned upstream commit declares `minSdk 30`. This fork restores the
  installation floor to `minSdk 29` while keeping `compileSdk 35`,
  `targetSdk 35`, and the existing single `arm64-v8a` APK strategy.
- `lib_base_network` gains a shared client-side failure model
  (`ApiResult`, `ApiError`, `BusinessException`, `apiSafeCall`) whose structure
  is borrowed from upstream work in the `0b536408..36823f74` range. Only the
  structure is reused: the code is rewritten under this fork's
  `com.justwen.androidnga.base.network` package with a sealed result/error
  model instead of upstream's nullable data/error pair, and it changes no
  transport, Cookie, converter, or retry behavior.
- An optional Miuix appearance is built on
  [compose-miuix-ui](https://github.com/compose-miuix-ui/miuix) `0.8.8`
  (Apache License 2.0), declared as Maven dependencies. Vector drawables named
  `ic_miuix_*` that carry a "from compose-miuix-ui miuix-icons 0.8.8" header
  are converted from `MiuixIcons` path data of the same release; the thumb
  up/down, globe, text format and emoticon drawables are original outline icons
  drawn for this app.
  `miuix_alert_dialog*.xml` layouts are derived from AndroidX AppCompat 1.7.1
  `abc_alert_dialog_*_material.xml` (Apache License 2.0) and keep its view ids.

No credentials, cookies, keystores, local Android SDKs, build output, or user
content are part of the imported source.
