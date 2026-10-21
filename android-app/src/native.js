// Bridge between the shared reader (../../reader/index.html) and Android.
// The reader only talks to window.MG_NATIVE and the window.MG_* hooks it defines,
// so the same page still runs unchanged as a web artifact.
import { Capacitor, registerPlugin, SystemBars, SystemBarsStyle } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Share } from '@capacitor/share';
import { Clipboard } from '@capacitor/clipboard';
import { TextToSpeech } from '@capacitor-community/text-to-speech';

if (Capacitor.isNativePlatform()) {
  const ReaderBridge = registerPlugin('ReaderBridge');
  let appReady = false;
  let fileQueued = false;

  async function deliverFile() {
    if (!appReady) { fileQueued = true; return; }
    try {
      const f = await ReaderBridge.takePendingFile();
      if (f && f.data && window.MG_openFile) await window.MG_openFile(f);
    } catch (e) {
      if (window.MG_toast) window.MG_toast("Couldn't read that file. Try adding it from the library instead.", 4500);
    }
  }

  window.MG_NATIVE = {
    platform: Capacitor.getPlatform(),
    share: (text, title) => Share.share({ text, title, dialogTitle: 'Share quote' }),
    copy: (text) => Clipboard.write({ string: text }),
    // Uses the phone's own voices, which work offline once installed.
    speak: (text, lang) => TextToSpeech.speak({ text, lang: lang || 'en-US', rate: 0.95 }),
    stopSpeaking: () => TextToSpeech.stop().catch(() => {}),
    setReading: (opts) => ReaderBridge.setReading(opts).catch(() => {}),
    // 'press' (long press), 'tick' (a small confirmation like a highlight or bookmark)
    haptic: (type) => ReaderBridge.haptic({ type }).catch(() => {}),
    // false hands Back to Android, so its predictive back-to-home preview plays when nothing is open
    setBackHandled: (on) => App.toggleBackButtonHandler({ enabled: !!on }).catch(() => {}),
    // dark === true: light icons for a dark page; false: dark icons; null: follow the system theme
    setBars: (dark) =>
      SystemBars.setStyle({
        style: dark === true ? SystemBarsStyle.Dark : dark === false ? SystemBarsStyle.Light : SystemBarsStyle.Default,
      }).catch(() => {}),
    // Called by the reader once its hooks exist.
    ready: () => {
      appReady = true;
      if (fileQueued) { fileQueued = false; deliverFile(); }
    },
  };

  App.addListener('backButton', () => {
    const handled = window.MG_back ? window.MG_back() : false;
    if (!handled) App.minimizeApp();
  });
  // While reading, Android's selection toolbar is suppressed and the reader's own bar (colours, Note, Copy, Look Up) is used.
  ReaderBridge.getInfo().then((info) => { if (info && info.quietSelection) window.MG_NATIVE.quietSelection = true; }).catch(() => {});
  ReaderBridge.addListener('volumeKey', (e) => {
    if (window.MG_volumeKey) window.MG_volumeKey(e && e.direction);
  });
  // Fired for "Open with" / "Share to Marginalia"; retained until this listener exists on cold start.
  ReaderBridge.addListener('fileAvailable', () => deliverFile());
}
