package org.xbmc.nanisore.utils.scheduling;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import org.xbmc.nanisore.utils.values.AndroidLoaderCache;
import org.xbmc.nanisore.utils.values.Store;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs tasks in a loader.
 *
 * Values are kept until the user explicitly leaves the activity, meaning
 * they survive config changes like device rotation.
 */
public class AndroidLoaderRunner extends BaseRunner {

    private static class Loadable<T> extends Loader<Task.Result<T>> {
        private final ExecutorService jobs;
        private final Task<T> task;
        private final Handler uiHandler = new Handler(Looper.getMainLooper());
        private volatile boolean stale = true;
        private Future<?> pending;
        private Task.Result<T> result;

        Loadable(Context context, Task<T> task, ExecutorService jobs) {
            super(context);
            this.task = task;
            this.jobs = jobs;
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
                    } catch (Throwable e) {
                        error = e;
                    } finally {
                        result = new Task.Result<>(value, error);
                        stale = false;
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

    private class Callback<T> implements LoaderManager.LoaderCallbacks<Task.Result<T>> {
        private final Task<T> task;
        private final Continuation<T> handler;

        Callback(Task<T> task, Continuation<T> handler) {
            this.task = task;
            this.handler = handler;
        }

        @Override
        public Loader<Task.Result<T>> onCreateLoader(int id, Bundle args) {
            return new Loadable<>(context, task, jobs);
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
    private final ExecutorService jobs;

    public AndroidLoaderRunner(FragmentActivity activity) {
        this(activity, (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public AndroidLoaderRunner(Fragment fragment) {
        this(fragment, (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public AndroidLoaderRunner(FragmentActivity activity, ExecutorService jobs) {
        manager = activity.getSupportLoaderManager();
        context = activity.getApplicationContext();
        this.jobs = jobs;
    }

    public AndroidLoaderRunner(Fragment fragment, ExecutorService jobs) {
        manager = fragment.getLoaderManager();
        context = fragment.getContext().getApplicationContext();
        this.jobs = jobs;
    }

    /**
     * Runs a task in the background then calls the handler in the UI thread.
     *
     * If there exists a loader with the same id as the task, that loader will
     * be used instead and this task is ignored and never executed.
     *
     * @param task ALWAYS USE A {@link Task<T>} INSTANCE WITH AN EXPLICIT ID!
     *             Otherwise a new loader will be made every time and there'd
     *             be no point in using this instead of a simpler impl like
     *             {@link ExecutorRunner}.
     *
     * @return The canceller also destroys the loader with the corresponding
     * id in addition to cancelling it. This allows you to replace the loader
     * when you want to invalidate its result.
     */
    @Override
    public <T> Canceller schedule(Producer<T> task, final Continuation<T> handler) {
        Task<T> t = Task.unit(task);
        final int id = t.id.hashCode();
        final Loader<Task.Result<T>> loader =
                manager.initLoader(id, null, new Callback<>(t, handler));
        return new Canceller() {
            @Override
            public void cancel() {
                loader.cancelLoad();
                manager.destroyLoader(id);
            }
        };
    }

    public Store asStore() {
        return new AndroidLoaderCache(context, manager);
    }

}
