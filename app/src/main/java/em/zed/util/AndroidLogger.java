package em.zed.util;

/*
 * This file is a part of the Kore project.
 */

import android.util.Log;

import org.xbmc.kore.BuildConfig;

public class AndroidLogger implements LogLevel.Logger {
    private final String tag;

    public AndroidLogger(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return BuildConfig.DEBUG;
    }

    @Override
    public void log(LogLevel level, String message) {
        switch (level) {
            case D:
                Log.d(tag, message);
                break;
            case I:
                Log.i(tag, message);
                break;
            case E:
                Log.e(tag, message);
                break;
        }
    }

    @Override
    public void log(LogLevel level, Throwable error, String message) {
        switch (level) {
            case D:
                Log.d(tag, message, error);
                break;
            case I:
                Log.i(tag, message, error);
                break;
            case E:
                Log.e(tag, message, error);
                break;
        }
    }
}
