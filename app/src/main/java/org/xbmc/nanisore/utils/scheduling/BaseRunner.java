package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.Either;

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
    public Canceller schedule(Producer action) {
        return schedule(action, NOOP);
    }

    @Override
    public <T> void once(final Producer<T> task, Continuation<T> handler) {
        final Either<Throwable, T> either = new Either<>();
        Canceller canceller = schedule(new Producer<Void>() {
            @Override
            public Void apply() {
                try {
                    either.right(task.apply());
                } catch (Throwable error) {
                    either.left(error);
                }
                return null;
            }
        });
        try {
            handler.accept(either.get(), null);
        } catch (Throwable error) {
            handler.accept(null, error);
        }
        canceller.cancel();
    }

    @Override
    public void once(Producer task) {
        once(task, NOOP);
    }

}
