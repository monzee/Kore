package org.xbmc.nanisore.utils.values;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

public class AndroidLoaderCache implements Store {

    private static class Holder<T> extends Loader<T> {
        final T value;

        Holder(Context context, T value) {
            super(context);
            this.value = value;
        }

        /**
         * This is really just to play nicely with other LoaderManager clients.
         *
         * This is useless normally because our callback has no onLoadFinished
         * implementation. But other callers of LoaderManager.initLoader might
         * receive this instance (if they somehow guessed the id) so we should
         * still deliver.
         */
        @Override
        protected void onStartLoading() {
            deliverResult(value);
        }
    }

    private static class Put<T> implements LoaderManager.LoaderCallbacks<T> {
        private final Context context;
        private final T value;

        Put(Context context, T value) {
            this.context = context;
            this.value = value;
        }

        @Override
        public Loader<T> onCreateLoader(int id, Bundle args) {
            return new Holder<>(context, value);
        }

        @Override
        public void onLoadFinished(Loader<T> loader, T data) {}

        @Override
        public void onLoaderReset(Loader<T> loader) {}
    }

    private static class Canary<T> extends Put<T> {
        boolean hasLoader = true;

        Canary(Context context, T value) {
            super(context, value);
        }

        @Override
        public Loader<T> onCreateLoader(int id, Bundle args) {
            hasLoader = false;
            return super.onCreateLoader(id, args);
        }
    }

    private final Context context;
    private final LoaderManager manager;

    public AndroidLoaderCache(FragmentActivity activity) {
        this(activity.getApplicationContext(), activity.getSupportLoaderManager());
    }

    public AndroidLoaderCache(Fragment fragment) {
        this(fragment.getContext().getApplicationContext(), fragment.getLoaderManager());
    }

    public AndroidLoaderCache(Context context, LoaderManager manager) {
        this.context = context;
        this.manager = manager;
    }

    @Override
    public <T> void put(String key, T value) {
        int id = id(key);
        manager.destroyLoader(id);
        manager.initLoader(id, null, new Put<>(context, value));
    }

    @Override
    public <T> void softPut(String key, T value) {
        manager.initLoader(id(key), null, new Put<>(context, value));
    }

    @Override
    public <T> T get(String key, T orElse) {
        int id = id(key);
        Canary<T> canary = new Canary<>(context, orElse);
        try {
            Holder<T> holder = (Holder<T>) manager.initLoader(id, null, canary);
            if (!canary.hasLoader) {
                manager.destroyLoader(id);
            }
            return holder.value;
        } catch (ClassCastException e) {
            // id clash with an existing loader.
            return orElse;
        }
    }

    @Override
    public <T> T hardGet(String key, T orElse) {
        try {
            return (
                    (Holder<T>) manager.initLoader(id(key), null, new Put<>(context, orElse))
            ).value;
        } catch (ClassCastException e) {
            return orElse;
        }
    }

    private static int id(String key) {
        String id = "loader-cache:" + key;
        return id.hashCode();
    }

}
