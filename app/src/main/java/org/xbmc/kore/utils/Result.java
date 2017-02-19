package org.xbmc.kore.utils;

/**
 * A type that wraps an Either instance to enable error recovery.
 *
 * When an Either goes into an error state, it can never come back to the
 * successful state because the subsequent steps are skipped and the error
 * is sent straight to the #match() receiver. This adds another level to the
 * monadic context and it can inspect the current Either, making it possible
 * to return to the successful state by generating a new Either.
 *
 * @see Either
 * @param <E> The error type of the wrapped Either
 * @param <T> The success type of the wrapped Either
 */
public class Result<E, T> implements Either<E, T> {

    /**
     * Static factory.
     *
     * This type is technically in an Io monad but that is hidden from the API
     * so you only need to pass Eithers.
     *
     * @param either The inner Either
     * @param <E> The error type
     * @param <T> The success type
     * @return an Io monad wrapping an Either instance. This also implements
     * the Either interface so you could pass it to an Either.Monad.
     */
    public static <E, T> Result<E, T> of(final Either<E, T> either) {
        return new Result<>(new Io<Either<E, T>>() {
            @Override
            public void run(Action<? super Either<E, T>> action) {
                action.got(either);
            }
        });
    }

    private final Io<Either<E, T>> eitherIo;

    private Result(Io<Either<E, T>> eitherIo) {
        this.eitherIo = eitherIo;
    }

    /**
     * Runs the Io action and matches the inner Either.
     *
     * @param matcher Handlers for the possible Either values.
     */
    @Override
    public void match(final Either.Pattern<? super E, ? super T> matcher) {
        eitherIo.run(new Io.Action<Either<E, T>>() {
            @Override
            public void got(Either<E, T> either) {
                either.match(matcher);
            }
        });
    }

    /**
     * Calls the #tee(Pattern) of the inner Either.
     *
     * @see Either.Monad#tee(Either.Pattern)
     */
    public Result<E, T> tee(final Either.Pattern<? super E, ? super T> matcher) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, T>>() {
            @Override
            public Either<E, T> from(Either.Monad<E, T> value) {
                return value.tee(matcher);
            }
        });
    }

    /**
     * Starts a new inner Either from the current Either.
     *
     * This allows recovery from a failed Either. If the transform doesn't have
     * any logic for when the Either fails, use {@link #then(Either.Bind)}
     * instead. If a new Either will be created even if the current one is
     * successful, use {@link #reset(Either)} instead.
     *
     * This is sort of the #map() of the outer Io monad with the source value
     * wrapped in an {@link Either.Monad} for convenience and the target
     * type constrained to Either E. There is no equivalent for Io#flatMap().
     *
     * The error type cannot be changed. There's no technical reason why it
     * can't be changed; it's to maintain the semantics of the inner monad
     * which also can't change its error type.
     *
     * @param transform A function that returns a new Either
     * @param <U> The type of the new Either
     * @return an Io monad with a new Either.
     */
    public <U> Result<E, U>
    recover(final Transform<Either.Monad<E, T>, Either<E, U>> transform) {
        return new Result<>(new Io<Either<E, U>>() {
            @Override
            public void run(final Action<? super Either<E, U>> uEitherAction) {
                eitherIo.run(new Action<Either<E, T>>() {
                    @Override
                    public void got(Either<E, T> tEither) {
                        transform.from(Either.Monad.of(tEither)).match(new Either.Pattern<E, U>() {
                            @Override
                            public void ok(U u) {
                                uEitherAction.got(Either.Monad.<E, U>ok(u));
                            }

                            @Override
                            public void fail(E error) {
                                uEitherAction.got(Either.Monad.<E, U>fail(error));
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Ignores the current result, successful or not, and starts a new Either.
     *
     * It's like {@link #recover(Transform)} except it doesn't need to inspect
     * the current Either. Analogous to {@link Either.Monad#then(Either)} but it
     * does it in the layer above it.
     *
     * @param uEither The replacement Either
     * @param <U> Its type
     * @return an Io object with a new inner Either.
     */
    public <U> Result<E, U> reset(final Either<E, U> uEither) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, U>>() {
            @Override
            public Either<E, U> from(Either.Monad<E, T> ignored) {
                return uEither;
            }
        });
    }

    /**
     * Calls the #then(Bind) of the inner Either.
     *
     * @see Either.Monad#then(Either.Bind)
     */
    public <U> Result<E, U> then(final Either.Bind<E, T, U> transform) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, U>>() {
            @Override
            public Either<E, U> from(Either.Monad<E, T> tEither) {
                return tEither.then(transform);
            }
        });
    }

    /**
     * Calls the #then(Either) of the inner Either.
     *
     * @see Either.Monad#then(Either)
     */
    public <U> Result<E, U> then(final Either<E, U> uEither) {
        return then(new Either.Bind<E, T, U>() {
            @Override
            public Either<E, U> from(T ignored) {
                return uEither;
            }
        });
    }

    /**
     * Calls the #map(Transform) of the innter Either.
     *
     * @see Either.Monad#map(Transform)
     */
    public <U> Result<E, U> map(final Transform<T, U> transform) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, U>>() {
            @Override
            public Either<E, U> from(Either.Monad<E, T> value) {
                return value.map(transform);
            }
        });
    }
}
