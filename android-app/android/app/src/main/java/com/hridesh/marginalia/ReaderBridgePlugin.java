package com.hridesh.marginalia;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Small bridge for the reader page:
 *  - hands over books opened with "Open with" / "Share to Marginalia"
 *  - turns pages with the volume keys while a book is open
 *  - keeps the screen on while a book is open
 *  - hides the status and navigation bars while a book is open (a swipe from the edge shows them briefly)
 */
@CapacitorPlugin(name = "ReaderBridge")
public class ReaderBridgePlugin extends Plugin {

    private static final long MAX_BOOK_BYTES = 150L * 1024 * 1024;

    private static volatile ReaderBridgePlugin instance;
    static volatile boolean volumeKeysEnabled = false;
    /** While a book is open, Android's selection toolbar is replaced by the reader's own bar (see ReaderWebView). */
    static volatile boolean readerMenuEnabled = false;
    /** True while a book is open: the system bars stay hidden. */
    static volatile boolean immersive = false;

    private Uri pendingUri;

    @Override
    public void load() {
        instance = this;
    }

    static ReaderBridgePlugin get() {
        return instance;
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        Uri uri = bookUriFrom(intent);
        if (uri == null) return;
        synchronized (this) {
            pendingUri = uri;
        }
        notifyListeners("fileAvailable", new JSObject(), true);
    }

    @SuppressWarnings("deprecation")
    private static Uri bookUriFrom(Intent intent) {
        if (intent == null) return null;
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) return intent.getData();
        if (Intent.ACTION_SEND.equals(action)) {
            if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return null;
    }

    @PluginMethod
    public void takePendingFile(PluginCall call) {
        Uri uri;
        synchronized (this) {
            uri = pendingUri;
            pendingUri = null;
        }
        JSObject ret = new JSObject();
        if (uri == null) {
            call.resolve(ret);
            return;
        }
        try {
            byte[] bytes = readAll(uri);
            ret.put("name", displayName(uri));
            ret.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
            call.resolve(ret);
        } catch (Exception | OutOfMemoryError e) {
            call.reject("Couldn't read that file", e instanceof Exception ? (Exception) e : null);
        }
    }

    @PluginMethod
    public void setReading(PluginCall call) {
        final boolean reading = Boolean.TRUE.equals(call.getBoolean("reading", false));
        final boolean volumeKeys = Boolean.TRUE.equals(call.getBoolean("volumeKeys", false));
        final boolean keepAwake = Boolean.TRUE.equals(call.getBoolean("keepAwake", false));
        volumeKeysEnabled = reading && volumeKeys;
        readerMenuEnabled = reading;
        immersive = reading;
        getActivity()
            .runOnUiThread(() -> {
                applyImmersive(getActivity().getWindow());
                if (reading && keepAwake) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        call.resolve();
    }

    /** Hides the status and navigation bars while reading, shows them again in the library. */
    static void applyImmersive(Window window) {
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
        if (immersive) {
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    /** A short vibration on long press, or a lighter tick when a highlight or bookmark is made. */
    @PluginMethod
    public void haptic(PluginCall call) {
        final String type = call.getString("type", "tick");
        getActivity()
            .runOnUiThread(() -> {
                int feedback;
                if ("press".equals(type)) feedback = HapticFeedbackConstants.LONG_PRESS;
                else if (Build.VERSION.SDK_INT >= 30) feedback = HapticFeedbackConstants.CONFIRM;
                else feedback = HapticFeedbackConstants.CLOCK_TICK;
                getBridge().getWebView().performHapticFeedback(feedback);
            });
        call.resolve();
    }

    /** Tells the reader that Android's selection toolbar is suppressed on this device, so it shows its own bar. */
    @PluginMethod
    public void getInfo(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("quietSelection", ReaderWebView.installed);
        call.resolve(ret);
    }

    void onVolumeKey(String direction) {
        JSObject data = new JSObject();
        data.put("direction", direction);
        notifyListeners("volumeKey", data);
    }

    private byte[] readAll(Uri uri) throws IOException {
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No data");
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BOOK_BYTES) throw new IOException("File is too large");
                outStream.write(buf, 0, n);
            }
            return outStream.toByteArray();
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContext().getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) name = c.getString(i);
                }
            } catch (Exception ignored) {}
        }
        if (name == null) name = uri.getLastPathSegment();
        return name != null ? name : "book.epub";
    }
}
