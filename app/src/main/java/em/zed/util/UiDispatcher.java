package em.zed.util;

/*
 * This file is a part of the Kore project.
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public abstract class UiDispatcher implements State.Dispatcher {

    public static final State.Dispatcher LOG_AND_RETHROW = new UiDispatcher() {
        @Override
        public void handle(final Throwable error) {
            Log.e(TAG, "Error while dispatching an action", error);
            throw new RuntimeException(error);
        }
    };

    public static final State.Dispatcher JUST_LOG = new UiDispatcher() {
        @Override
        public void handle(final Throwable error) {
            Log.e(TAG, "Error while dispatching an action", error);
        }
    };

    private static final String TAG = UiDispatcher.class.getSimpleName();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void run(Runnable block) {
        if (Thread.currentThread() == MAIN_HANDLER.getLooper().getThread()) {
            block.run();
        } else {
            MAIN_HANDLER.post(block);
        }
    }

}
