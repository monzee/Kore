package org.xbmc.kore.utils.scheduling;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs tasks in a loader.
 *
 * Values are kept until the activity is killed, meaning they survive config
 * changes like device rotation.
 */
public class LoaderRunner extends BaseRunner {

    private static class Loadable<T> extends Loader<Task.Result<T>> {
        // why doesn't this warn me?
        private static final ExecutorService jobs = (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR;
        private final Task<T> task;
        private final Handler uiHandler;
        private volatile boolean stale = true;
        private Future<?> pending;
        private Task.Result<T> result;

        public Loadable(Context context, Task<T> task) {
            super(context);
            uiHandler = new Handler(Looper.getMainLooper());
            this.task = task;
        }

        @Override
        protected void onForceLoad() {
            if (pending != null) {
                return;
            }
            pending = jobs.submit(new Runnable() {
                @Override
                public void run() {
                    T value = null;
                    Throwable error = null;
                    try {
                        value = task.apply();
                        stale = false;
                    } catch (Throwable e) {
                        error = e;
                    } finally {
                        result = new Task.Result<>(value, error);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                deliverResult(result);
                                pending = null;
                            }
                        });
                    }
                }
            });
        }

        @Override
        protected void onStartLoading() {
            if (stale) {
                forceLoad();
            } else {
                deliverResult(result);
            }
        }

        @Override
        protected void onReset() {
            cancelLoad();
            stale = true;
            result = null;
        }

        @Override
        protected boolean onCancelLoad() {
            if (pending == null) {
                return false;
            } else {
                pending.cancel(true);
                pending = null;
                return true;
            }
        }
    }

    private static class Callback<T> implements LoaderManager.LoaderCallbacks<Task.Result<T>> {
        private final Context context;
        private final Task<T> task;
        private final Continuation<T> handler;

        private Callback(Context context, Task<T> task, Continuation<T> handler) {
            this.context = context;
            this.task = task;
            this.handler = handler;
        }

        @Override
        public Loader<Task.Result<T>> onCreateLoader(int id, Bundle args) {
            return new Loadable<>(context, task);
        }

        @Override
        public void onLoadFinished(Loader<Task.Result<T>> loader, Task.Result<T> data) {
            handler.accept(data.value, data.error);
        }

        @Override
        public void onLoaderReset(Loader<Task.Result<T>> loader) {
        }
    }

    private final LoaderManager manager;
    private final Context context;

    public LoaderRunner(FragmentActivity activity) {
        manager = activity.getSupportLoaderManager();
        context = activity.getApplicationContext();
    }

    public LoaderRunner(Fragment fragment) {
        manager = fragment.getLoaderManager();
        context = fragment.getContext();
    }

    /**
     * Runs a task in a background thread then calls the handler in the
     * UI thread.
     *
     * If there exists a loader with the same id as the task, its value will
     * be passed instead in the handler and this task is ignored and never
     * executed.
     *
     * @param task ALWAYS USE A {@see Task<T>} INSTANCE WITH AN EXPLICIT ID!
     *             Otherwise a new loader will be made every time and there's
     *             no point in using this instead of a simpler impl.
     *
     * @return The canceller also destroys the loader with the same id as the
     * task in addition to cancelling it. This allows you to replace the
     * loader when you don't want to reuse its value.
     */
    @Override
    public <T> Canceller schedule(Producer<T> task, final Continuation<T> handler) {
        Task<T> t = Task.unit(task);
        final int id = t.id.hashCode();
        final Loader<Task.Result<T>> loader =
                manager.initLoader(id, null, new Callback<>(context, t, handler));
        return new Canceller() {
            @Override
            public void cancel() {
                loader.cancelLoad();
                manager.destroyLoader(id);
            }
        };
    }

}
