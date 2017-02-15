package org.xbmc.kore.utils;

public class Result<E, T> {

    public static <E, T> Result<E, T> of(final Either<E, T> either) {
        return new Result<>(new Io<Either<E, T>>() {
            @Override
            public void run(Action<? super Either<E, T>> action) {
                action.got(either);
            }
        });
    }

    private final Io<Either<E, T>> eitherIo;

    public Result(Io<Either<E, T>> eitherIo) {
        this.eitherIo = eitherIo;
    }

    public void match(Io.Action<Either<E, T>> action) {
        eitherIo.run(action);
    }

    public void match(final Either.Pattern<? super E, ? super T> matcher) {
        match(new Io.Action<Either<E, T>>() {
            @Override
            public void got(Either<E, T> either) {
                either.match(matcher);
            }
        });
    }

    public Result<E, T> tee(Either.Pattern<? super E, ? super T> action) {
        match(action);
        return this;
    }

    public <U> Result<E, U>
    recover(final Transform<Either.Monad<E, T>, Either<E, U>> transform) {
        return new Result<>(new Io<Either<E, U>>() {
            @Override
            public void run(final Action<? super Either<E, U>> uEitherAction) {
                match(new Action<Either<E, T>>() {
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

    public <U> Result<E, U> reset(final Either<E, U> uEither) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, U>>() {
            @Override
            public Either<E, U> from(Either.Monad<E, T> ignored) {
                return uEither;
            }
        });
    }

    public <U> Result<E, U> then(final Either.Bind<E, T, U> transform) {
        return recover(new Transform<Either.Monad<E, T>, Either<E, U>>() {
            @Override
            public Either<E, U> from(Either.Monad<E, T> tEither) {
                return tEither.then(transform);
            }
        });
    }

    public <U> Result<E, U> then(final Either<E, U> uEither) {
        return then(new Either.Bind<E, T, U>() {
            @Override
            public Either<E, U> from(T ignored) {
                return uEither;
            }
        });
    }
}
