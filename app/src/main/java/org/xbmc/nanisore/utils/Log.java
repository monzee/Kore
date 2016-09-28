package org.xbmc.nanisore.utils;

public enum Log {
    D, I, E;

    public void to(Console console, String message) {
        if (console != null) {
            console.log(this, message);
        }
    }

    public void to(Console console, String tpl, Object... xs) {
        if (console != null) {
            console.log(this, String.format(tpl, xs));
        }
    }
}
