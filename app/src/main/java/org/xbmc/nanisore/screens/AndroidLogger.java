package org.xbmc.nanisore.screens;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.widget.Toast;

import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.Log;

public class AndroidLogger implements Console {

    private final String tag;
    private final @Nullable Context context;

    public AndroidLogger(String tag) {
        this(null, tag);
    }

    public AndroidLogger(Context context, String tag) {
        this.context = context.getApplicationContext();
        this.tag = tag;
    }

    @Override
    public void tell(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } else {
            log(Log.I, message);
        }
    }

    @Override
    public void log(Log level, String message) {
        switch (level) {
            case D:
                android.util.Log.d(tag, message);
                break;
            case I:
                android.util.Log.i(tag, message);
                break;
            case E:
                android.util.Log.e(tag, message);
                break;
        }
    }

    @Nullable
    protected String getString(@StringRes int resId, Object... fmtArgs) {
        if (context != null) {
            return context.getString(resId, fmtArgs);
        } else {
            return null;
        }
    }
}
