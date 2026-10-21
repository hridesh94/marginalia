package com.hridesh.marginalia;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.getcapacitor.CapacitorWebView;

/**
 * Capacitor's WebView, with one change: while a book is open, the text-selection toolbar shows
 * Marginalia's actions (Highlight, Note, Look Up, Copy, Share) instead of Android's defaults,
 * so there is a single menu rather than two competing ones. The reader handles each action.
 * Text fields (the note editor, search) keep Android's normal menu, so Paste is still there.
 */
public class ReaderWebView extends CapacitorWebView {

    static volatile boolean installed = false;

    private static final int ID_HIGHLIGHT = 0x4d01;
    private static final int ID_NOTE = 0x4d02;
    private static final int ID_LOOKUP = 0x4d03;
    private static final int ID_COPY = 0x4d04;
    private static final int ID_SHARE = 0x4d05;

    public ReaderWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        installed = true;
    }

    private boolean useReaderMenu() {
        return ReaderBridgePlugin.readerMenuEnabled && !onCheckIsTextEditor();
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        if (useReaderMenu()) return super.startActionMode(new ReaderCallback(callback), type);
        return super.startActionMode(callback, type);
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        if (useReaderMenu()) return super.startActionMode(new ReaderCallback(callback));
        return super.startActionMode(callback);
    }

    private static final class ReaderCallback extends ActionMode.Callback2 {

        private final ActionMode.Callback inner;

        ReaderCallback(ActionMode.Callback inner) {
            this.inner = inner;
        }

        private static void fill(Menu menu) {
            menu.clear();
            menu.add(Menu.NONE, ID_HIGHLIGHT, 1, "Highlight");
            menu.add(Menu.NONE, ID_NOTE, 2, "Note");
            menu.add(Menu.NONE, ID_LOOKUP, 3, "Look Up");
            menu.add(Menu.NONE, ID_COPY, 4, "Copy");
            menu.add(Menu.NONE, ID_SHARE, 5, "Share");
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Let the WebView set up its selection state first, then replace its items with ours.
            boolean ok = inner.onCreateActionMode(mode, menu);
            fill(menu);
            return ok;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            inner.onPrepareActionMode(mode, menu);
            fill(menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            String action;
            switch (item.getItemId()) {
                case ID_HIGHLIGHT: action = "highlight"; break;
                case ID_NOTE: action = "note"; break;
                case ID_LOOKUP: action = "lookup"; break;
                case ID_COPY: action = "copy"; break;
                case ID_SHARE: action = "share"; break;
                default: return inner.onActionItemClicked(mode, item);
            }
            ReaderBridgePlugin plugin = ReaderBridgePlugin.get();
            if (plugin != null) plugin.onSelectionAction(action);
            return true; // the reader clears the selection once it has used it, which closes this toolbar
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            inner.onDestroyActionMode(mode);
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            // Keeps the toolbar placed next to the selected text.
            if (inner instanceof ActionMode.Callback2) ((ActionMode.Callback2) inner).onGetContentRect(mode, view, outRect);
            else super.onGetContentRect(mode, view, outRect);
        }
    }
}
