package org.xbmc.kore.utils;

/**
 * A computation that could possibly fail.
 *
 * <p>
 * Suppose we have a service that synchronously returns a Post object but fails
 * sometimes:
 * <pre>
 *     Post latest() throws Unavailable {...}
 * </pre>
 * We can model a call to this service method with an Either:
 * <pre>
 *     Either[Unavailable, Post] getLatest = new Either[Unavailable, Post]() {
 *         public void match(Pattern[Unavailable, Post] caller) {
 *             try {
 *                 caller.ok(service.latest());
 *             } catch (Unavailable e) {
 *                 caller.fail(e);
 *             }
 *         }
 *     };
 * </pre>
 * This object IS a call to the service method. It DOES NOT call the method on
 * its own. To actually call the service, you must match its possible return
 * values:
 * <pre>
 *     getLatest.match(new Pattern[Unavailable, Post]() {
 *         public void ok(Post post) {
 *             display(post);
 *         }
 *         public void error(Unavailable error) {
 *             report(error);
 *         }
 *     });
 * </pre>
 * You cannot do something like <code>Post p = getLatest.ok()</code> or ask
 * <code>boolean ok = getLatest.isSuccessful()</code> like you'd probably
 * expect. This ensures that you handle both possible cases at all times. If
 * you squint, it looks like a checked exception call except the call happens
 * outside the block and the error might not even be throwable (it is in
 * this case).
 * <p>
 * Suppose we have a whole bunch of service calls and we wish to do one after
 * the other but only if it succeeds. If we encounter an error once, we want
 * to receive it in the end to handle it.
 * <pre>
 *     User postAuthor(Post p) throws SomeException;
 *     List[Post] allPostsBy(User u) throws AnotherException;
 *     List[Comments] allCommentsIn(Post p) throws Exception;
 * </pre>
 * We want to get all comments in every post made by the author of the latest
 * post. The new methods require an argument and thus cannot be modeled by an
 * Either alone. We need to construct an Either factory, Bind:
 * <pre>
 *     Either.Bind[Err, Post, User] getPostAuthor = new Either.Bind[Err, Post, User]() {
 *         public Either[Err, User] from(final Post p) {
 *             return new Either[Err, User]() {
 *                 public void match(Pattern[Err, User] patt) {
 *                     try {
 *                         patt.ok(service.postAuthor(p));
 *                     } catch (Exception e) {
 *                         patt.fail(new Err(e));
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 * The others are built similarly but since java7 syntax is so painful:
 * <pre>
 *     Either.Bind[Err, User, List[Post]] getAllPostsByUser = user -> patt -> {
 *         try {
 *             patt.ok(service.allPostsBy(user));
 *         } catch (Exception e) {
 *             patt.fail(new Err(e));
 *         }
 *     };
 *     Either.Bind[Err, Post, List[Comments]] getAllCommentsInPost = ...;
 * </pre>
 * By using the monad extensions, we can compose the calls. I said we'd get all
 * the comments in all posts. We can't do that because there's no list monad or
 * transformer here. Let's just get the comments in the first post of the user
 * instead:
 * <pre>
 *     Either.Monad.of(getLatest)
 *          .then(getPostAuthor)
 *          .then(getAllPostsByUser)
 *          .map(posts -> posts.get(0))
 *          .then(getAllCommentsInPost)
 *          .match(new Either.Pattern[Err, List[Comments]]() {
 *              public void ok(List[Comments] comments) {}
 *              public void fail(Err e) {}
 *          })
 * </pre>
 * If any of the Eithers fails, the Err object would be passed down the chain
 * without executing the Bind or the map. The error would ultimately be
 * received by the #fail(Err) method in the pattern.
 * <p>
 * You might be asking, "why not just call the methods normally?" and you'd be
 * absolutely right to wonder! If you're lucky to have a synchronous API, call
 * them normally and add asynchronicity at the call site only. But if your
 * service layer only exposes asynchronous methods, you could use this pattern
 * and wrap the callbacks to avoid the "pyramid of doom". An asynchronous API
 * for the first example might look like this:
 * <pre>
 *     void latest(Callback[Post] cb) {...}
 *     ...
 *     latest(new Callback[Post]() {
 *         public void onSuccess(Post p) {
 *             displayPost(p);
 *         }
 *         public void onError(Unavailable e) {
 *             report(e);
 *         }
 *     });
 * </pre>
 * The `getLatest` Either would be defined as such:
 * <pre>
 *     Either[Unavailable, Post] getLatest = patt -> latest(new Callback[Post]() {
 *         public void onSuccess(Post p) {
 *             patt.ok(p);
 *         }
 *         public void onError(Unavailable e) {
 *             patt.fail(e);
 *         }
 *     });
 * </pre>
 * Imagine how a sequence of asynchronous calls would look like. Imagine how
 * many times you'd repeat yourself to handle the errors at every level. Now
 * imagine conditionals which call slightly different methods at each branch.
 * <p>
 * With Either, the usage would be the exactly like shown before. It's a
 * uniform API that does not care how the data arrives. Much worse than a sync
 * API but also much better than callback hell.
 *
 * @param <E> The type of the object describing the error.
 * @param <V> The type of the result of a successful computation.
 */
public interface Either<E, V> {

    /**
     * Access the result of the computation.
     *
     * @param matcher A callback object that handles both the successful and
     *                erroneous result of the computation.
     */
    void match(Pattern<? super E, ? super V> matcher);

    /**
     * The callback interface.
     *
     * The intention here is to call either #ok() or #fail() inside a
     * {@link Either#match(Pattern)} implementation only once. That is not
     * really enforceable by the language though. Nothing stops you from
     * calling both in the same branch multiple times.
     *
     * It may be interesting to call #ok() multiple times then call #fail() in
     * the end to simulate a stream of events or trigger a downstream request
     * to be sent multiple times.
     *
     * @param <E> The error type.
     * @param <V> The success type.
     */
    interface Pattern<E, V> {
        void ok(V value);
        void fail(E error);
    }

    /**
     * A function that receives a successful value and turns it into a new
     * Either with possibly different type.
     *
     * Only the success type can change. The error type remains the same.
     *
     * @param <E> The error type.
     * @param <T> The old success type.
     * @param <U> The new success type.
     */
    interface Bind<E, T, U> {
        Either<E, U> from(T value);
    }

    /**
     * A wrapper for {@link Either} objects that gives them monadic properties.
     *
     * If this were Java8, these would have been static and default methods in
     * the Either interface.
     *
     * @param <E> The error type.
     * @param <V> The success type.
     */
    class Monad<E, V> implements Either<E, V> {

        /**
         * Static factory to prevent Monad objects from being re-wrapped.
         *
         * @param either The object to wrap.
         * @param <E> The error type.
         * @param <V> The success type.
         * @return a monadic Either.
         */
        public static <E, V> Monad<E, V> of(Either<E, V> either) {
            if (either instanceof Monad) {
                return (Monad<E, V>) either;
            }
            return new Monad<>(either);
        }

        /**
         * Static factory of the successful variant of the Either.
         *
         * @param value The value to wrap in a successful Either.
         * @param <E> The error type.
         * @param <V> The type of the value.
         * @return a monadic successful Either.
         */
        public static <E, V> Monad<E, V> ok(final V value) {
            return new Monad<>(new Either<E, V>() {
                @Override
                public void match(Pattern<? super E, ? super V> matcher) {
                    matcher.ok(value);
                }
            });
        }

        /**
         * Static factory of the failure variant of the Either.
         *
         * @param error The error object to wrap.
         * @param <E> The type of the object.
         * @param <V> The success type.
         * @return a monadic failed Either.
         */
        public static <E, V> Monad<E, V> fail(final E error) {
            return new Monad<>(new Either<E, V>() {
                @Override
                public void match(Pattern<? super E, ? super V> matcher) {
                    matcher.fail(error);
                }
            });
        }

        private final Either<E, V> either;

        private Monad(Either<E, V> either) {
            this.either = either;
        }

        @Override
        public void match(Pattern<? super E, ? super V> matcher) {
            either.match(matcher);
        }

        /**
         * Performs a match on the pattern then returns the Either.
         *
         * Useful for debugging. You can insert a tee between <code>#then()</code>
         * calls to inspect the value of the Either at that point.
         *
         * @param matcher A pattern object to match.
         * @return the same Either object
         */
        public Monad<E, V> tee(Pattern<E, V> matcher) {
            match(matcher);
            return this;
        }

        /**
         * Changes the value of a successful Either.
         *
         * The transform function will not be executed when this Either has
         * failed.
         *
         * This might not be used often because you can just do the
         * transformation before you actually use it. One important use case
         * though is for changing the value to match the types of other Eithers
         * that may be returned in a function.
         *
         * @param transform A transform function.
         * @param <NV> The new success type, which could be the same as the old.
         *             The error type cannot be changed.
         * @return a monadic Either with a possibly different type.
         */
        public <NV> Monad<E, NV> map(final Transform<V, NV> transform) {
            return new Monad<>(new Either<E, NV>() {
                @Override
                public void match(final Pattern<? super E, ? super NV> matcher) {
                    either.match(new Pattern<E, V>() {
                        @Override
                        public void ok(V v) {
                            matcher.ok(transform.from(v));
                        }

                        @Override
                        public void fail(E error) {
                            matcher.fail(error);
                        }
                    });
                }
            });
        }

        /**
         * Changes the value of a successful Either to a new success value or
         * an error value.
         *
         * The error value of the new Either must be the same as the old.
         *
         * The transform function will not be executed when this Either has
         * failed.
         *
         * It is not possible to recover from a failure or even know that there
         * is an error in the middle of a sequence. This is the reason why the
         * Either monad is combined with Io in {@link Result}. If you don't need
         * such a feature, you could just use Either instead of Result.
         *
         * This is traditionally called "flatMap" in JVM languages and ">>="
         * or "bind" in functional languages.
         *
         * @param transform A function that generates a new Either from the
         *                  success value of this Either.
         * @param <NV> The success type of the new Either, which may be the
         *             same as the old.
         * @return a monadic Either with a possibly different type.
         */
        public <NV> Monad<E, NV> then(final Bind<E, V, NV> transform) {
            return new Monad<>(new Either<E, NV>() {
                @Override
                public void match(final Pattern<? super E, ? super NV> matcher) {
                    either.match(new Pattern<E, V>() {
                        @Override
                        public void ok(V v) {
                            transform.from(v).match(matcher);
                        }

                        @Override
                        public void fail(E error) {
                            matcher.fail(error);
                        }
                    });
                }
            });
        }

        /**
         * Replaces the Either with a new one of possibly different type.
         *
         * This is the same as {@link #then(Bind)}, except 1) this does not care
         * about the success value and 2) the other one generates a new Either
         * lazily whereas you need to provide an existing one here. The latter
         * is not really a big deal since the Either itself is still lazy.
         *
         * @param next The new Either.
         * @param <NV> Its success type.
         * @return a monadic Either.
         */
        public <NV> Monad<E, NV> then(final Either<E, NV> next) {
            return then(new Bind<E, V, NV>() {
                @Override
                public Either<E, NV> from(V ignored) {
                    return next;
                }
            });
        }
    }
}
