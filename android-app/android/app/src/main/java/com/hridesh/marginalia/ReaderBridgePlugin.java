package com.hridesh.marginalia;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.WindowManager;
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
 */
@CapacitorPlugin(name = "ReaderBridge")
public class ReaderBridgePlugin extends Plugin {

    private static final long MAX_BOOK_BYTES = 150L * 1024 * 1024;

    private static volatile ReaderBridgePlugin instance;
    static volatile boolean volumeKeysEnabled = false;
    /** While a book is open, the selection toolbar shows the reader's own actions (see ReaderWebView). */
    static volatile boolean readerMenuEnabled = false;

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
        getActivity()
            .runOnUiThread(() -> {
                if (reading && keepAwake) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        call.resolve();
    }

    /** Tells the reader whether the single selection menu is available on this device. */
    @PluginMethod
    public void getInfo(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("selectionMenu", ReaderWebView.installed);
        call.resolve(ret);
    }

    void onSelectionAction(String action) {
        JSObject data = new JSObject();
        data.put("action", action);
        notifyListeners("selectionAction", data);
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
