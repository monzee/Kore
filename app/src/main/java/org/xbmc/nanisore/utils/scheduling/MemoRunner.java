package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.values.Store;
import org.xbmc.nanisore.utils.values.WeakStore;

import java.util.HashMap;
import java.util.Map;

public class MemoRunner extends BaseRunner implements CachingRunner {
    private final Runner delegate;
    private final Map<String, Call> cache = new HashMap<>();

    private static class Call {
        Task.Result<?> result;
        Canceller canceller;
    }

    public MemoRunner(Runner delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> Canceller schedule(Producer<T> task, final Continuation<T> handler) {
        final Task<T> t = Task.unit(task);
        synchronized (this) {
            if (!cache.containsKey(t.id)) {
                final Call c = new Call();
                final Canceller canceller = delegate.schedule(t, new Continuation<T>() {
                    @Override
                    public void accept(T result, Throwable error) {
                        c.result = new Task.Result<>(result, error);
                        handler.accept(result, error);
                    }
                });
                c.canceller = new Canceller() {
                    @Override
                    public void cancel() {
                        canceller.cancel();
                        cache.remove(t.id);
                    }
                };
                cache.put(t.id, c);
                return c.canceller;
            }
        }
        Call c = cache.get(t.id);
        //noinspection unchecked
        handler.accept((T) c.result.value, c.result.error);
        return c.canceller;
    }

    @Override
    public Store toStore() {
        Store store = new WeakStore();
        for (String key : cache.keySet()) {
            store.put(key, cache.get(key).result.value);
        }
        return store;
    }

}
