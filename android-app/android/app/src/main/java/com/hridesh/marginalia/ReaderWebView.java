package com.hridesh.marginalia;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.PopupMenu;
import com.getcapacitor.CapacitorWebView;

/**
 * Capacitor's WebView, with one change: while a book is open, Android's text-selection toolbar
 * (Copy, Translate, Select all…) is not shown. The selection and its handles stay, and the reader
 * shows its own bar with highlight colours, Note, Copy and Look Up instead.
 * Text fields (the note editor, search) keep Android's normal menu, so Paste is still there.
 */
public class ReaderWebView extends CapacitorWebView {

    static volatile boolean installed = false;

    public ReaderWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        installed = true;
    }

    private boolean quietSelection() {
        return ReaderBridgePlugin.readerMenuEnabled && !onCheckIsTextEditor();
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        // The WebView keeps the selection only while it holds an action mode, so hand it one that shows nothing.
        if (quietSelection()) return new SilentActionMode(getContext(), this);
        return super.startActionMode(callback, type);
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        if (quietSelection()) return new SilentActionMode(getContext(), this);
        return super.startActionMode(callback);
    }

    /** An action mode with no toolbar. The WebView calls these methods while the selection changes; none needs to do anything. */
    private static final class SilentActionMode extends ActionMode {

        private final Context context;
        private final Menu menu;

        SilentActionMode(Context context, View anchor) {
            this.context = context;
            this.menu = new PopupMenu(context, anchor).getMenu();
        }

        @Override
        public int getType() {
            return ActionMode.TYPE_FLOATING;
        }

        @Override
        public void setTitle(CharSequence title) {}

        @Override
        public void setTitle(int resId) {}

        @Override
        public void setSubtitle(CharSequence subtitle) {}

        @Override
        public void setSubtitle(int resId) {}

        @Override
        public void setCustomView(View view) {}

        @Override
        public void invalidate() {}

        @Override
        public void invalidateContentRect() {}

        @Override
        public void hide(long duration) {}

        @Override
        public void finish() {}

        @Override
        public Menu getMenu() {
            return menu;
        }

        @Override
        public CharSequence getTitle() {
            return null;
        }

        @Override
        public CharSequence getSubtitle() {
            return null;
        }

        @Override
        public View getCustomView() {
            return null;
        }

        @Override
        public MenuInflater getMenuInflater() {
            return new MenuInflater(context);
        }
    }
}
