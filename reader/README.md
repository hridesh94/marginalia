# Marginalia — ebook reader

A single-file, mobile-first ebook reader (`index.html`). It renders books with epub.js and keeps the library, reading position, highlights, bookmarks and settings in the browser (IndexedDB for book files, localStorage for everything else).

- **Any common ebook format.** Files are recognised by their content, not their extension. EPUB is kept as-is (embedded fonts are de-obfuscated so they display). MOBI/AZW/AZW3, PDF, FB2/FBZ, CBZ, TXT and HTML are converted to EPUB once, at import, so highlights, search, bookmarks and positions work the same everywhere. DRM-protected books (Kindle store, Adobe, Apple, LCP) and KFX are refused with a clear message.
- **The book's own design by default.** Publisher fonts, sizes, spacing, alignment and headings are left alone; Options overrides font, line spacing and alignment one at a time ("Book" restores the original). Fixed-layout books (PDF, comics, fixed-layout EPUB) render as printed and centred. Right-to-left books turn the right way.
- **Page turns.** Flip (default), Slide, Fade or None. Swipes follow the finger and can be cancelled. Reduced-motion settings switch to Fade.
- **Chrome follows Apple Books; reading features follow Kindle:** tap zones, pages left in chapter, time-left estimate, four-colour highlights with notes, dictionary lookup, bookmark ribbon, resume to the exact position. Six themes: Original, Quiet, Paper, Bold, Calm, Focus.

The MOBI (foliate-js), PDF (pdf.js) and zlib (fflate) converters load on demand from jsDelivr; the Android build bundles them instead.

The file has no `<html>`/`<head>` wrapper of its own. The Android build (`android-app/scripts/build-web.mjs`) wraps it in a full document; to host it on the web, wrap it the same way, with a `viewport-fit=cover` viewport meta.
