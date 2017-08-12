package em.zed.util;

/*
 * This file is a part of the Kore project.
 */

public enum LogLevel {
    D, I, E;

    public void to(Logger logger, String tpl, Object... fmtArgs) {
        if (logger != null && logger.isEnabled(this)) {
            logger.log(this, String.format(tpl, fmtArgs));
        }
    }

    public void to(Logger logger, Throwable error, String tpl, Object... fmtArgs) {
        if (logger != null && logger.isEnabled(this)) {
            logger.log(this, error, String.format(tpl, fmtArgs));
        }
    }

    public interface Logger {
        boolean isEnabled(LogLevel level);

        void log(LogLevel level, String message);

        void log(LogLevel level, Throwable error, String message);
    }
}
