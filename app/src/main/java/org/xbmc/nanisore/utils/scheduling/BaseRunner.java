package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.Either;
import org.xbmc.nanisore.utils.Try;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class with default implementations for schedule() and once().
 *
 * IMPORTANT! You should override one of the two (or both) binary schedule()
 * methods! The default implementations call each other!
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class BaseRunner implements Runner {

    private static final Continuation NOOP = new Continuation<Object>() {
        @Override
        public void accept(Object result, Throwable error) {}
    };

    @Override
    public <T> Canceller schedule(Producer<T> task, final Continuation<T> handler) {
        return schedule(task, new Handler<T>() {
            @Override
            public void then(Try<T> result) {
                try {
                    handler.accept(result.get(), null);
                } catch (Throwable e) {
                    handler.accept(null, e);
                }
            }
        });
    }

    @Override
    public <T> Canceller schedule(Producer<T> task, final Handler<T> handler) {
        return schedule(task, new Continuation<T>() {
            @Override
            public void accept(T result, Throwable error) {
                Either<Throwable, T> either = new Either<>();
                if (error != null) {
                    either.left(error);
                } else {
                    either.right(result);
                }
                handler.then(either);
            }
        });
    }

    @Override
    public Canceller schedule(Producer action) {
        return schedule(action, NOOP);
    }

    @Override
    public <T> void once(final Producer<T> task, final Continuation<T> handler) {
        once(task, new Handler<T>() {
            @Override
            public void then(Try<T> result) {
                try {
                    handler.accept(result.get(), null);
                } catch (Throwable e) {
                    handler.accept(null, e);
                }
            }
        });
    }

    @Override
    public <T> void once(final Producer<T> task, final Handler<T> handler) {
        final AtomicReference<Canceller> canceller = new AtomicReference<>();
        final AtomicBoolean shouldCancelNow = new AtomicBoolean(false);
        canceller.set(schedule(task, new Handler<T>() {
            @Override
            public void then(Try<T> result) {
                handler.then(result);
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
    public void once(Producer task) {
        once(task, NOOP);
    }

}
