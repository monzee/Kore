package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.values.Either;
import org.xbmc.nanisore.utils.values.Try;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class with default implementations for schedule() and once().
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class BaseRunner implements Runner {

    private static final Continuation NOOP = new Continuation<Object>() {
        @Override
        public void accept(Object result, Throwable error) {}
    };

    @Override
    public <T> Canceller schedule(Producer<T> task, final Try.Handler<T> handler) {
        return schedule(task, toContinuation(handler));
    }

    @Override
    public Canceller schedule(Producer action) {
        return schedule(action, NOOP);
    }

    @Override
    public <T> void once(Producer<T> task, final Continuation<T> handler) {
        final AtomicReference<Canceller> canceller = new AtomicReference<>();
        final AtomicBoolean shouldCancelNow = new AtomicBoolean(false);
        canceller.set(schedule(task, new Continuation<T>() {
            @Override
            public void accept(T result, Throwable error) {
                handler.accept(result, error);
                Canceller c = canceller.get();
                if (c != null) {
                    c.cancel();
                } else {
                    // if the canceller is null, it means this handler was
                    // called immediately/sequentially in the same thread as
                    // the caller (either the runner is synchronous or there
                    // is a cached value waiting for the handler)
                    // we should signal outside that the canceller should
                    // be called ASAP.
                    shouldCancelNow.set(true);
                }
            }
        }));
        if (shouldCancelNow.get()) {
            canceller.get().cancel();
        }
    }

    @Override
    public <T> void once(Producer<T> task, final Try.Handler<T> handler) {
        once(task, toContinuation(handler));
    }

    @Override
    public void once(Producer task) {
        once(task, NOOP);
    }

    private static <T> Continuation<T> toContinuation(final Try.Handler<T> handler) {
        return new Continuation<T>() {
            @Override
            public void accept(T result, Throwable error) {
                Either<Throwable, T> either = new Either<>();
                if (error != null) {
                    either.left(error);
                } else {
                    either.right(result);
                }
                handler.got(either);
            }
        };
    }
}
