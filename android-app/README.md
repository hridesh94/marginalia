# Marginalia for Android

Capacitor wrapper that turns the reader in `../reader/index.html` into an installable APK. The reader page stays the single source: `npm run build` wraps it into `www/`, swaps the CDN scripts and Google Fonts for bundled copies, and adds `src/native.js`, the bridge to Android.

## Getting the APK

Once release signing is set up (below), every signed build is published as a GitHub Release. The newest one is always at this permanent link, which works without a GitHub account:

https://github.com/hridesh94/marginalia/releases/latest/download/Marginalia.apk

Each run of **Android APK** in GitHub Actions also keeps its APK as a run artifact for 30 days (signed in to GitHub only). Builds made before the signing secrets exist are test builds and are not released.

## Release signing (one-time)

Builds only update in place when they are signed with the same key. Add two repository secrets under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `MARGINALIA_KEYSTORE_BASE64` | The PKCS#12 keystore, base64-encoded on one line |
| `MARGINALIA_KEYSTORE_PASSWORD` | Its password, used for both the store and the `marginalia` key |

Without them the workflow still produces a test APK signed with a throwaway key. Each test APK has to be uninstalled before the next one installs, which deletes the library on the phone.

Keep a backup of the keystore. If it's lost, future builds can't update the installed app.

## What the Android build adds

- Works offline: epub.js, JSZip, the fonts (Charis SIL for the Charter-style default, Literata, Gelasio for Georgia) and the format converters (foliate-js for MOBI/AZW3, pdf.js with its CMaps, standard fonts and image decoders, fflate) ship inside the APK.
- Back button closes the innermost popup or sheet, then the book, then sends the app to the background.
- "Open with" and "Share to" Marginalia for EPUB, MOBI/AZW3, PDF, FB2, CBZ and plain-text files. Generic `application/octet-stream` files are accepted too, because many file managers label ebooks that way; the app checks the content and explains when a file isn't a book.
- Volume keys turn pages, and the screen stays on while a book is open. Both can be switched off in Text & Display → More options.
- An offline English dictionary: `scripts/build-dict.mjs` turns WordNet 3.1 into small JSON files under `www/dict/` (about 17 MB on disk, 6 MB inside the APK), so a lookup reads only a few hundred kilobytes. WordNet's licence ships alongside the data.
- Speak aloud uses the phone's own text-to-speech voices, which work offline.
- Full screen while reading: the status and navigation bars are hidden while a book is open (a swipe from the edge shows them briefly) and come back in the library.
- One selection bar: while a book is open, `ReaderWebView` keeps Android's text-selection toolbar (Copy, Translate, Select all…) from appearing, so only Marginalia's bar with highlight colours, Note, Copy and Look Up shows. Text fields keep Android's menu, so Paste still works.
- Native share and clipboard, and status-bar icons that follow the reading theme.

## Building locally

Needs Node 22, JDK 21 and the Android SDK (Android Studio installs it).

```sh
cd android-app
npm ci
npm run sync          # builds www/ and copies it into the Android project
cd android && ./gradlew assembleDebug
```

Set `MARGINALIA_KEYSTORE_PATH` and `MARGINALIA_KEYSTORE_PASSWORD` before `./gradlew assembleRelease` to sign a release build.
