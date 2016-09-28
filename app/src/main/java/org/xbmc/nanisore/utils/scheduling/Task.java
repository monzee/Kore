package org.xbmc.nanisore.utils.scheduling;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Producer with an id and some handy functions. The id is useful for caching.
 */
public class Task<T> implements Producer<T> {

    public static class Result<T> {
        public final T value;
        public final Throwable error;

        public Result(T value, Throwable error) {
            this.value = value;
            this.error = error;
        }
    }

    public interface Transform<T, U> {
        U apply(T t) throws Throwable;
    }

    public final String id;
    private final Producer<T> delegate;
    private static final AtomicInteger IDS = new AtomicInteger(1);

    public Task(String id, Producer<T> delegate) {
        this.id = id;
        this.delegate = delegate;
    }

    public Task(Producer<T> delegate) {
        this("task-" + IDS.getAndIncrement(), delegate);
    }

    /**
     * aka return
     */
    public static <T> Task<T> unit(Producer<T> fn) {
        return unit(null, fn);
    }

    public static <T> Task<T> unit(String id, Producer<T> fn) {
        return fn instanceof Task ? (Task<T>) fn
                : id == null ? new Task<>(fn)
                : new Task<>(id, fn);
    }

    public static <T> Task<T> unit(final Future<T> future) {
        return unit(new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                return future.get();
            }
        });
    }

    public static <T> Task<T> unit(String id, final Future<T> future) {
        return unit(id, new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                return future.get();
            }
        });
    }

    public static <T> Task<T> just(final T value) {
        return unit(new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                return value;
            }
        });
    }

    public static <T> Task<T> just(String id, final T value) {
        return unit(id, new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                return value;
            }
        });
    }

    /**
     * aka fmap
     */
    public <U> Task<U> map(final Transform<T, U> fn) {
        return new Task<>(id, new Producer<U>() {
            @Override
            public U apply() throws Throwable {
                return fn.apply(delegate.apply());
            }
        });
    }

    /**
     * aka >>=, flatMap
     */
    public <U> Task<U> bind(final Transform<T, Producer<U>> fn) {
        return new Task<>(id, new Producer<U>() {
            @Override
            public U apply() throws Throwable {
                return fn.apply(delegate.apply()).apply();
            }
        });
    }

    @Override
    public T apply() throws Throwable {
        return delegate.apply();
    }

}
