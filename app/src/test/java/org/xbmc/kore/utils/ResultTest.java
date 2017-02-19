package org.xbmc.kore.utils;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ResultTest {
    AtomicBoolean flag = new AtomicBoolean();

    @Before
    public void setup() {
        flag.set(false);
    }

    @Test
    public void strawman() {
        AtomicBoolean intermediate = new AtomicBoolean(false);
        Result.of(Either.Monad.<Void, Boolean>ok(false))
                .then(Either.Monad.<Void, Boolean>fail(null))
                .tee(EitherTest.failure(intermediate))
                .recover(new Transform<Either.Monad<Void, Boolean>, Either<Void, Void>>() {
                    @Override
                    public Either<Void, Void> from(final Either.Monad<Void, Boolean> value) {
                        return new Either<Void, Void>() {
                            @Override
                            public void match(final Pattern<? super Void, ? super Void> matcher) {
                                value.match(new Pattern<Void, Boolean>() {
                                    @Override
                                    public void ok(Boolean value) {
                                        Assert.fail();
                                    }

                                    @Override
                                    public void fail(Void error) {
                                        matcher.ok(null);
                                    }
                                });
                            }
                        };
                    }
                })
                .tee(EitherTest.expect(null))
                .map(new Transform<Void, Boolean>() {
                    @Override
                    public Boolean from(Void value) {
                        return true;
                    }
                })
                .tee(EitherTest.expect(true))
                .match(EitherTest.success(flag));
        assertTrue(intermediate.get());
        assertTrue(flag.get());
    }

    @Test
    public void can_get_a_successful_either_from_a_failed_either() {
        AtomicBoolean intermediate = new AtomicBoolean(false);
        Result.of(Either.Monad.<Void, Void>fail(null))
                .tee(EitherTest.failure(intermediate))
                .recover(new Transform<Either.Monad<Void, Void>, Either<Void, String>>() {
                    @Override
                    public Either<Void, String> from(Either.Monad<Void, Void> value) {
                        return Either.Monad.ok("recovered");
                    }
                })
                .tee(EitherTest.expect("recovered"))
                .match(EitherTest.success(flag));
        assertTrue(intermediate.get());
        assertTrue(flag.get());

        flag.set(false);
        intermediate.set(false);
        Result.of(Either.Monad.<Void, Void>fail(null))
                .tee(EitherTest.failure(intermediate))
                .reset(Either.Monad.<Void, String>ok("ok"))
                .tee(EitherTest.expect("ok"))
                .match(EitherTest.success(flag));
        assertTrue(intermediate.get());
        assertTrue(flag.get());
    }

}