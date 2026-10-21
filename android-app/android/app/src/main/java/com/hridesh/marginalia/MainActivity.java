package com.hridesh.marginalia;

import android.os.Bundle;
import android.view.KeyEvent;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(ReaderBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }

    /** Volume down turns forward, volume up turns back, while a book is open and the option is on. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if ((code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP) && ReaderBridgePlugin.volumeKeysEnabled) {
            ReaderBridgePlugin plugin = ReaderBridgePlugin.get();
            if (plugin != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    plugin.onVolumeKey(code == KeyEvent.KEYCODE_VOLUME_DOWN ? "next" : "prev");
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
