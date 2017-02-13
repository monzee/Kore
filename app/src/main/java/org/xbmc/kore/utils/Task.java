package org.xbmc.kore.utils;

import android.support.annotation.NonNull;

/**
 * Represents a computation that will eventually complete.
 *
 * A normal call in an imperative language like Java looks like
 * <pre>
 *     Object interestingValue = someService.slowlyCompute(params);
 * </pre>
 * There are times when you cannot afford to wait for the method call to
 * complete or simply cannot do the call because of platform restrictions. For
 * example, you cannot do synchronous network requests in the UI thread in
 * Android. In such a case, the callback-passing style is employed:
 * <pre>
 *     httpService.get(path, query, new Callback() {
 *         public void onSuccess(String response) {
 *             // do something with the response
 *         }
 *         public void onError(Exception e) {
 *             // error handling
 *         }
 *     });
 * </pre>
 * This is asynchronous; the next statement will be executed immediately
 * without waiting for the computation to complete. When it does, one of the
 * methods in the callback will be invoked by the `httpService` depending on
 * how the request went.
 *
 * This type is a simple model of such. A task defines a possibly (but not
 * required to be) asynchronous procedure and when it's done, it calls a method
 * passed to it instead of returning a value.
 *
 * The primary advantage offered by this abstraction is composability. Suppose
 * that you have two async methods (imagine square brackets as angled):
 * <pre>
 *     void getUserWithEmail(String email, Callback[User] cb);
 *     void getPostsBy(User user, Callback[List[Post]] cb);
 * </pre>
 * If you wanted to get a list of posts made by a user with a certain email,
 * you would do something like:
 * <pre>
 *     getUserWithEmail("foo@example.com", new Callback[User]() {
 *         public void ok(User user) {
 *             getPostsBy(user, new Callback[List[Post]]() {
 *                 public void ok(List[Post] posts) {
 *                     for (Post p : posts) {
 *                         display(p);
 *                     }
 *                 }
 *             });
 *         }
 *     });
 * </pre>
 * The actual logic is embedded very deep in the callbacks. Hard to follow,
 * gets even worse when you have more async steps to do to get to the data you
 * want. This is called "callback hell" or "pyramid of doom".
 *
 * It could be expressed with tasks as such:
 * <pre>
 *     Task[User] getUserWithEmail(String email);
 *     Task[List[Post]] getPostsBy(User user);
 * </pre>
 * And the client code:
 * <pre>
 *     new Task.Sequence[](getUserWithEmail("foo@example.com"), new Bind[User, List[Post]]() {
 *         public Task[List[Post]] from(User user) {
 *             return getPostsBy(user);
 *         }
 *     }).start(new OnFinish[List[Post]]() {
 *         public void got(List[Post] posts) {
 *             for (Post p : posts) {
 *                 display(p);
 *             }
 *         }
 *     });
 * </pre>
 * Instead of nesting, you add more steps by chaining more tasks or binds to
 * the sequence. Looks better in java8 syntax honestly. Retrolambda recommended.
 *
 * @param <T> The type of the value being computed or awaited.
 */
public interface Task<T> {

    /**
     * Starts the computation, invokes a procedure when done.
     *
     * Tasks are lazy; instantiating a task or returning one from a method does
     * not perform any work. This method has to be called with a continuation
     * in order to actually start working.
     *
     * The procedure may be called any number of times, but it's usually once
     * or never. A task that doesn't invoke its {@link OnFinish} is considered
     * a failed task.
     *
     * @param then The consumer of the value being computed.
     */
    void start(@NonNull OnFinish<? super T> then);

    /**
     * A procedure that awaits a value.
     *
     * Also called "continuation" elsewhere. One task corresponds to exactly
     * one continuation. A task is considered to be "successful" when it calls
     * its continuation, otherwise it has "failed".
     *
     * There is no way to recover from a failure if the task simply ignores
     * its continuation. In such a case, it is assumed that the task instead
     * has called an error reporting or handling routine instead of the
     * continuation.
     *
     * If it is required for the system to defer the error handling to the
     * highest/outermost caller, then 1) the continuation must always be called
     * by the task even if it has failed and 2) the error condition must be
     * encoded in the type T of the task/continuation. An example would be to
     * use a Task[Option[T]] type instead of just Task[T]. An Option[T] will
     * always be passed to the continuation and the continuation can then
     * introspect the Option to decide how to proceed.
     *
     * No such type is provided here. It is up to the clients to code them up
     * for their use case.
     *
     * @param <T> The type of the value generated by a task.
     */
    interface OnFinish<T> {
        void got(T result);
    }

    /**
     * A function that produces a task from a value.
     *
     * Used for linking a task that depends on the value produced by another
     * task in a {@link Sequence}.
     *
     * @param <T> The value to derive a task from.
     * @param <U> The type of the derived task.
     */
    interface Bind<T, U> {
        Task<? extends U> from(T value);
    }

    /**
     * A collection of tasks that will be called in sequence as long as the
     * components call their continuations.
     *
     * @param <T> The type of the value generated by the last task.
     */
    class Sequence<T, U> implements Task<U> {
        private final Task<T> producer;
        private final Bind<? super T, U> make;

        /**
         * Starts a sequence with a task.
         *
         * Shortcut for <code>new Sequence[](Just.some(null), e -> task)</code>
         *
         * @param task The first (technically second) task to do.
         * @param <T> The type of the task.
         * @return a sequence.
         */
        public static <T> Sequence<?, T> of(final Task<T> task) {
            return new Sequence<>(NIL, new Bind<Object, T>() {
                @Override
                public Task<T> from(Object value) {
                    return task;
                }
            });
        }

        private static Task<?> NIL = Just.some(null);

        /**
         * Starts a sequence with a task and a bind that produces a task from
         * the value produced by the given task.
         *
         * @param producer of the value needed to produce this step.
         * @param make the task for this step from the value produced by the task.
         */
        public Sequence(Task<T> producer, Bind<? super T, U> make) {
            this.producer = producer;
            this.make = make;
        }

        /**
         * Continues the sequence with a task produced from the value produced
         * in this step.
         *
         * @param next The producer of the next task.
         * @param <V> The type of the next task.
         * @return a sequence one step longer.
         */
        public <V> Sequence<U, V> then(final Bind<? super U, V> next) {
            return new Sequence<>(this, next);
        }

        /**
         * Continues the sequence with a task, ignoring the value produced in
         * this step.
         *
         * @param next The next task.
         * @param <V> Its type.
         * @return a sequence one step longer.
         */
        public <V> Sequence<U, V> then(final Task<V> next) {
            return then(new Bind<U, V>() {
                @Override
                public Task<V> from(U value) {
                    return next;
                }
            });
        }

        @Override
        public void start(final @NonNull OnFinish<? super U> block) {
            producer.start(new OnFinish<T>() {
                @Override
                public void got(T result) {
                    make.from(result).start(block);
                }
            });
        }
    }

    /**
     * Static class for {@link Task} factories.
     */
    final class Just {

        /**
         * Turns a value into a task that produces the same value.
         *
         * @param result The value to wrap
         * @param <T> Its type
         * @return a task that may be passed to a {@link Sequence} or returned
         * by a {@link Bind}.
         */
        public static <T> Task<T> some(final T result) {
            return new Task<T>() {
                @Override
                public void start(@NonNull OnFinish<? super T> then) {
                    then.got(result);
                }
            };
        }

        /**
         * Produces a failed task.
         *
         * If passed to {@link Sequence#then(Task)} or returned by a
         * {@link Bind} passed to {@link Sequence#then(Bind)}, the subsequent
         * tasks in the sequence will not be invoked.
         *
         * @param <T> The success type of the task.
         * @return a failed task.
         */
        public static <T> Task<T> none() {
            return new Task<T>() {
                @Override
                public void start(@NonNull OnFinish<? super T> then) {
                }
            };
        }

        private Just() {
        }
    }

}
