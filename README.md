# Marginalia

A calm ebook reader for Android. It opens EPUB, Kindle MOBI and AZW3, PDF, FB2, CBZ comics, TXT and HTML books, keeps each book's own typography, and turns pages like paper. Your library stays on your phone.

**Website:** https://hridesh94.github.io/marginalia/

**Download:** [Marginalia.apk](https://github.com/hridesh94/marginalia/releases/latest/download/Marginalia.apk) (Android 7 and newer). Every version is on the [releases page](https://github.com/hridesh94/marginalia/releases).

## What's here

| Folder | Contents |
|---|---|
| `reader/` | The reader itself: a single, mobile-first web page built on epub.js, with the format converters. |
| `android-app/` | The Capacitor wrapper that turns the reader into the Android app, with its native bridge. |
| `landing/` | The one-page website, published to GitHub Pages. |
| `.github/workflows/` | `android.yml` builds the APK and publishes signed builds as releases; `pages.yml` publishes the website. |

See [`reader/README.md`](reader/README.md) for the reader's features and [`android-app/README.md`](android-app/README.md) for building and signing the app.
