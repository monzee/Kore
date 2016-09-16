package org.xbmc.kore.utils.scheduling;

import java.util.HashMap;
import java.util.Map;

public class CachingRunner extends BaseRunner {
    private final Runner delegate;
    private final Map<String, Call> cache = new HashMap<>();

    private static class Call {
        Task.Result<?> result;
        Canceller canceller;
    }

    public CachingRunner(Runner delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> Canceller schedule(Producer<T> task, final Continuation<T> handler) {
        final Task<T> t = Task.unit(task);
        if (cache.containsKey(t.id)) {
            Call c = cache.get(t.id);
            //noinspection unchecked
            handler.accept((T) c.result.value, c.result.error);
            return c.canceller;
        } else {
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

}
