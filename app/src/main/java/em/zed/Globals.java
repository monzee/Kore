package em.zed;

/*
 * This file is a part of the Kore project.
 */

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import em.zed.util.State;

public interface Globals {
    ExecutorService IO = Executors.newCachedThreadPool();
    ExecutorService COMPUTE = (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR;
    State.Dispatcher IMMEDIATE = new State.Dispatcher() {
        @Override
        public void run(Runnable block) {
            block.run();
        }

        @Override
        public void handle(Throwable error) {
            throw new RuntimeException(error);
        }
    };
    Globals DEFAULT = new Globals() {
        @Override
        public ExecutorService threadContext(final String name) {
            return Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable runnable) {
                    return new Thread(runnable, name);
                }
            });
        }
    };

    ExecutorService threadContext(String name);

}
