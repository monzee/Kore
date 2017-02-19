package org.xbmc.kore.utils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class EitherTest {
    private AtomicBoolean flag;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Before
    public void setup() {
        flag = new AtomicBoolean(false);
    }

    @AfterClass
    public static void teardown() {
        WORKER.shutdown();
    }

    @Test
    public void strawman() {
        Either.Monad.<Void, Integer>ok(10)
                .map(new Transform<Integer, Integer>() {
                    @Override
                    public Integer from(Integer value) {
                        return value * 20;
                    }
                })
                .tee(expect(200))
                .then(new Either.Bind<Void, Integer, String>() {
                    @Override
                    public Either<Void, String> from(Integer value) {
                        return Either.Monad.ok("value: " + value * value);
                    }
                })
                .tee(expect("value: 40000"))
                .then(Either.Monad.<Void, Boolean>ok(false))
                .tee(expect(false))
                .then(Either.Monad.<Void, Boolean>fail(null))
                .match(failure(flag));
        assertTrue(flag.get());
    }

    @Test
    public void named_either() {
        class E implements Either<Void, Boolean> {
            Boolean value;
            @Override
            public void match(Pattern<? super Void, ? super Boolean> matcher) {
                if (value != null) {
                    matcher.ok(value);
                } else {
                    matcher.fail(null);
                }
            }
        }

        E e = new E();
        e.match(failure(flag));
        assertTrue(flag.get());

        flag.set(false);
        e.value = true;
        Either.Monad.of(e).tee(expect(true)).match(success(flag));
        assertTrue(flag.get());

        flag.set(false);
        e.value = false;
        Either.Monad.of(e).tee(expect(false)).match(success(flag));
        assertTrue(flag.get());
    }

    @Test(expected = NullPointerException.class)
    public void match_pattern_cannot_be_null_for_successful_either() {
        Either.Monad.<Void, Void>ok(null)
                .match(null);
    }

    @Test(expected = NullPointerException.class)
    public void match_pattern_cannot_be_null_for_failed_either() {
        Either.Monad.<Void, Void>fail(null)
                .match(null);
    }

    @Test
    public void can_change_the_success_value_with_map() {
        Either.Monad.<Void, Integer>ok(1)
                .tee(expect(1))
                .map(new Transform<Integer, String>() {
                    @Override
                    public String from(Integer value) {
                        return "one";
                    }
                })
                .tee(expect("one"))
                .match(success(flag));
        assertTrue(flag.get());
    }

    @Test
    public void can_change_the_success_value_with_then() {
        final Object o = new Object();
        Either.Pattern<Void, Object> checkSame = new Either.Pattern<Void, Object>() {
            @Override
            public void ok(Object value) {
                assertSame(o, value);
            }

            @Override
            public void fail(Void error) {
                Assert.fail();
            }
        };

        Either.Monad.<Void, Void>ok(null)
                .tee(expect((Void) null))
                .then(new Either.Bind<Void, Void, Object>() {
                    @Override
                    public Either<Void, Object> from(Void value) {
                        return Either.Monad.ok(o);
                    }
                })
                .tee(checkSame)
                .match(success(flag));
        assertTrue(flag.get());

        flag.set(false);
        Either.Monad.<Void, Void>ok(null)
                .then(Either.Monad.<Void, Object>ok(o))
                .tee(checkSame)
                .match(success(flag));
        assertTrue(flag.get());
    }

    @Test
    public void can_change_success_to_failure_with_then() {
        Either.Monad.<Void, Void>ok(null)
                .then(new Either.Bind<Void, Void, Void>() {
                    @Override
                    public Either<Void, Void> from(Void value) {
                        return Either.Monad.fail(null);
                    }
                })
                .match(failure(flag));
        assertTrue(flag.get());

        flag.set(false);
        Either.Monad.<Void, Void>ok(null)
                .then(Either.Monad.<Void, Void>fail(null))
                .match(failure(flag));
        assertTrue(flag.get());
    }

    @Test
    public void cannot_change_the_failure_value() {
        Either.Monad.<Void, Void>fail(null)
                .map(new Transform<Void, Object>() {
                    @Override
                    public Object from(Void value) {
                        return new Object();
                    }
                })
                .match(failure(flag));
        assertTrue(flag.get());

        flag.set(false);
        Either.Monad.<Void, Void>fail(null)
                .then(new Either.Bind<Void, Void, Object>() {
                    @Override
                    public Either<Void, Object> from(Void value) {
                        return Either.Monad.ok(new Object());
                    }
                })
                .match(failure(flag));

        flag.set(false);
        Either.Monad.<Void, Void>fail(null)
                .then(Either.Monad.<Void, Void>ok(null))
                .match(failure(flag));
    }

    @Test
    public void success_value_will_be_lost_when_a_downstream_either_doesnt_call_its_matcher() {
        Either.Monad.<Void, String>ok("ok")
                .then(new Either<Void, Object>() {
                    @Override
                    public void match(Pattern<? super Void, ? super Object> matcher) {
                        flag.set(true);
                    }
                })
                .match(null);  // no NPE here, it won't get called.
        assertTrue(flag.get());

        flag.set(false);
        Either.Monad.<Void, String>ok("ok")
                .then(new Either.Bind<Void, String, Object>() {
                    @Override
                    public Either<Void, Object> from(String value) {
                        return new Either<Void, Object>() {
                            @Override
                            public void match(Pattern<? super Void, ? super Object> matcher) {
                                flag.set(true);
                            }
                        };
                    }
                })
                .match(null);  // no NPE here, it won't get called.
        assertTrue(flag.get());
    }
    @Test
    public void error_value_will_still_be_passed_even_if_downstream_either_doesnt_call_its_matcher() {
        Either.Monad.<Void, Void>ok(null)
                .then(Either.Monad.fail((Void) null))
                .then(new Either<Void, Object>() {
                    @Override
                    public void match(Pattern<? super Void, ? super Object> matcher) {
                    }
                })
                .match(failure(flag));
        assertTrue(flag.get());
    }

    @Test(timeout = 1000)
    public void tee_doesnt_cause_multiple_upstream_calls() throws InterruptedException {
        class Pending implements Either.Pattern<Void, Object> {
            final Either.Pattern<Void, Object> p;
            final CountDownLatch latch = new CountDownLatch(1);
            Pending(Either.Pattern<Void, Object> p) {
                this.p = p;
            }

            @Override
            public void ok(Object value) {
                p.ok(value);
                latch.countDown();
            }

            @Override
            public void fail(Void error) {
                p.fail(error);
                latch.countDown();
            }
        }

        class Async<T> implements Either<Void, T> {
            final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
            final T failSentinel;
            Async(T failSentinel) {
                this.failSentinel = failSentinel;
            }

            @Override
            public void match(final Pattern<? super Void, ? super T> matcher) {
                WORKER.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            T ok = queue.take();
                            if (ok.equals(failSentinel)) {
                                matcher.fail(null);
                            } else {
                                matcher.ok(ok);
                            }
                        } catch (InterruptedException e) {
                            matcher.fail(null);
                        }
                    }
                });
            }
        }

        Async<Integer> numbers = new Async<>(-1);
        Pending pending = new Pending(EitherTest.<Void, Object>success(flag));
        Either.Monad.of(numbers)
                .tee(expect(100))
                .map(new Transform<Integer, String>() {
                    @Override
                    public String from(Integer value) {
                        return "value: " + value / 10;
                    }
                })
                .tee(expect("value: 10"))
                .match(pending);
        assertFalse(flag.get());
        numbers.queue.put(100);
        // this will deadlock if Async#match was called more than once
        // because there's only one item in the queue
        pending.latch.await();
        assertTrue(flag.get());

        flag.set(false);
        pending = new Pending(EitherTest.<Void, Object>failure(flag));
        Either.Monad.of(numbers)
                .match(pending);
        numbers.queue.put(-1);
        pending.latch.await();
        assertTrue(flag.get());
    }

    static <T> Either.Pattern<Void, T> expect(final T expected) {
        return new Either.Pattern<Void, T>() {
            @Override
            public void ok(T actual) {
                assertEquals(expected, actual);
            }

            @Override
            public void fail(Void error) {
                Assert.fail("unexpected fail: " + error);
            }
        };
    }

    static <E, T> Either.Pattern<E, T> success(final AtomicBoolean done) {
        return new Either.Pattern<E, T>() {
            @Override
            public void ok(T value) {
                done.set(true);
            }

            @Override
            public void fail(E error) {
                Assert.fail("unexpected fail: " + error);
            }
        };
    }

    static <E, T> Either.Pattern<E, T> failure(final AtomicBoolean done) {
        return new Either.Pattern<E, T>() {
            @Override
            public void ok(T value) {
                Assert.fail("unexpected success: " + value);
            }

            @Override
            public void fail(E error) {
                done.set(true);
            }
        };
    }
}
