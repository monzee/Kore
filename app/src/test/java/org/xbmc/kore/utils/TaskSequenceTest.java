package org.xbmc.kore.utils;

import android.support.annotation.NonNull;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TaskSequenceTest {

    static final Task<?> SUCCESS = new Task<Object>() {
        @Override
        public void start(OnFinish<? super Object> then) {
            then.got(null);
        }
    };

    static final Task<?> FAILURE = new Task<Object>() {
        @Override
        public void start(OnFinish<? super Object> then) {
        }
    };

    static Task<?> hit(final AtomicInteger count, final Task<?> next) {
        return new Task<Object>() {
            @Override
            public void start(OnFinish<? super Object> then) {
                count.incrementAndGet();
                next.start(then);
            }
        };
    }

    static Task.OnFinish<Object> done(final AtomicInteger count) {
        return new Task.OnFinish<Object>() {
            @Override
            public void got(Object result) {
                count.incrementAndGet();
            }
        };
    }

    static Task.Bind<Integer, Integer> plus(final int n) {
        return new Task.Bind<Integer, Integer>() {
            @Override
            public Task<Integer> from(Integer value) {
                return Task.Just.some(value + n);
            }
        };
    }

    static Task.Bind<Integer, Integer> triple() {
        return new Task.Bind<Integer, Integer>() {
            @Override
            public Task<Integer> from(Integer value) {
                return Task.Just.some(value * 3);
            }
        };
    }

    static Task.Bind<Integer, Integer> square() {
        return new Task.Bind<Integer, Integer>() {
            @Override
            public Task<Integer> from(Integer value) {
                return Task.Just.some(value * value);
            }
        };
    }

    <T> Task.Bind<T, String> show(final String prefix) {
        return new Task.Bind<T, String>() {
            @Override
            public Task<String> from(T value) {
                return Task.Just.some(prefix + value);
            }
        };
    }

    @Test
    public void happy_path() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        Task.Sequence.of(SUCCESS)
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .start(done(count));
        assertEquals(3, count.get());
    }

    @Test
    public void does_nothing_until_start_is_called() {
        AtomicInteger count = new AtomicInteger(0);
        Task<?> seq = Task.Sequence.of(SUCCESS).then(hit(count, SUCCESS));
        assertEquals(0, count.get());
        seq.start(done(count));
        assertEquals(2, count.get());
    }

    @Test
    public void chain_is_stopped_when_a_continuation_is_not_called() {
        AtomicInteger count = new AtomicInteger(0);
        Task.Sequence.of(SUCCESS)
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .then(hit(count, FAILURE))
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .then(hit(count, SUCCESS))
                .start(done(count));
        assertEquals(4, count.get());  // number of successes before first failure
    }

    @Test
    public void tasks_are_called_in_order() {
        final StringBuilder sb = new StringBuilder();
        final AtomicBoolean done = new AtomicBoolean(false);
        Task.Sequence.of(new Task<Object>() {
            @Override
            public void start(OnFinish<? super Object> then) {
                sb.append(1);
                then.got(null);
            }
        }).then(new Task<Object>() {
            @Override
            public void start(OnFinish<? super Object> then) {
                sb.append(',').append(2);
                then.got(null);
            }
        }).then(new Task<Object>() {
            @Override
            public void start(OnFinish<? super Object> then) {
                sb.append(',').append(3);
                then.got(null);
            }
        }).then(new Task<String>() {
            @Override
            public void start(OnFinish<? super String> then) {
                then.got(sb.toString());
            }
        }).start(new Task.OnFinish<String>() {
            @Override
            public void got(String result) {
                assertEquals("1,2,3", result);
                done.set(true);
            }
        });
        assertTrue(done.get());
    }

    @Test
    public void monadic_bind() {
        final AtomicBoolean done = new AtomicBoolean(false);
        new Task.Sequence<>(Task.Just.some(5), plus(1))
                .then(square())
                .then(triple())
                .then(show("result: "))
                .start(new Task.OnFinish<String>() {
                    @Override
                    public void got(String result) {
                        assertEquals("result: 108", result);
                        done.set(true);
                    }
                });
        assertTrue(done.get());
    }

    /*
     * a sketch of the failure monad with task transformer
     */

    interface Either<L extends Exception, R> {
        R get() throws L;
    }

    interface Transform<T, U> {
        U from(T value) throws Exception;
    }

    interface GotResult<T> {
        void ok(T value);
        void err(Exception e);
    }

    interface BindResult<T, U> {
        void from(T value, GotResult<U> result);
    }

    static class Result<T> implements Either<Exception, T> {
        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> err(Exception e) {
            return new Result<>(null, e);
        }

        static <T, U> Task.Bind<Result<T>, Result<U>> lift(final Task.Bind<T, U> fn) {
            /*
            return result -> result.task((t, got) -> {
                try {
                    fn.from(t).start(u -> got.ok(u)));
                } catch (Exception e) {
                    got.err(e);
                }
            };
             */
            return new Task.Bind<Result<T>, Result<U>>() {
                @Override
                public Task<Result<U>> from(final Result<T> value) {
                    return value.task(new BindResult<T, U>() {
                        @Override
                        public void from(T value, final GotResult<U> got) {
                            try {
                                fn.from(value).start(new Task.OnFinish<U>() {
                                    @Override
                                    public void got(U result) {
                                        got.ok(result);
                                    }
                                });
                            } catch (Exception e) {
                                got.err(e);
                            }
                        }
                    });
                }
            };
        }


        final T value;
        final Exception error;

        private Result(T value, Exception error) {
            this.value = value;
            this.error = error;
        }

        <U> Result<U> map(Transform<? super T, U> fn) {
            if (error != null) {
                return err(error);
            }
            try {
                return ok(fn.from(value));
            } catch (Exception e) {
                return err(e);
            }
        }

        <U> Result<U> flatMap(Transform<? super T, Result<U>> fn) {
            if (error != null) {
                return err(error);
            }
            try {
                return fn.from(value);
            } catch (Exception e) {
                return err(e);
            }
        }

        Task<Result<T>> task() {
            return Task.Just.some(this);
        }

        <U> Task<Result<U>> task(final BindResult<T, U> fn) {
            return new Task<Result<U>>() {
                @Override
                public void start(final @NonNull OnFinish<? super Result<U>> then) {
                    if (error != null) {
                        then.got(Result.<U>err(error));
                    } else {
                        fn.from(value, new GotResult<U>() {
                            @Override
                            public void ok(U value) {
                                then.got(Result.ok(value));
                            }

                            @Override
                            public void err(Exception e) {
                                then.got(Result.<U>err(e));
                            }
                        });
                    }
                }
            };
        }

        @Override
        public T get() throws Exception {
            if (error == null) {
                return value;
            }
            throw error;
        }
    }

    Task.Bind<Result<Integer>, Result<Integer>> checkBounds(final int max) {
        return new Task.Bind<Result<Integer>, Result<Integer>>() {
            @Override
            public Task<Result<Integer>> from(Result<Integer> result) {
                return result.task(new BindResult<Integer, Integer>() {
                    @Override
                    public void from(Integer n, GotResult<Integer> got) {
                        if (n <= max) {
                            got.ok(n);
                        } else {
                            got.err(new IndexOutOfBoundsException("max is " + max));
                        }
                    }
                });
            }
        };
    }

    Task.Bind<String, String> checkLength(final int max) {
        return new Task.Bind<String, String>() {
            @Override
            public Task<? extends String> from(String value) {
                int length = value.length();
                if (length > max) {
                    throw new IllegalArgumentException(length + " is greater than " + max);
                }
                return Task.Just.some(value);
            }
        };
    }

    Task.Bind<Result<String>, Result<String>> format() {
        return new Task.Bind<Result<String>, Result<String>>() {
            @Override
            public Task<Result<String>> from(Result<String> value) {
                return value.map(new Transform<String, String>() {
                    @Override
                    public String from(String value) {
                        return value.toUpperCase();
                    }
                }).task();
            }
        };
    }

    @Test
    public void error_propagation_strawman() {
        final AtomicInteger c = new AtomicInteger(0);
        Task.Sequence
                .of(Result.ok(100).task())
                .then(checkBounds(100))
                .then(Result.lift(triple()))
                .then(Result.lift(plus(200)))
                .then(Result.lift(this.<Integer>show("result: ")))
                .then(format())
                .start(new Task.OnFinish<Result<String>>() {
                    @Override
                    public void got(Result<String> result) {
                        c.incrementAndGet();
                        try {
                            assertEquals("RESULT: 500", result.get());
                        } catch (Exception e) {
                            fail("unexpected exception");
                        }
                    }
                });
        Task.Sequence
                .of(Result.<Integer>err(new IllegalStateException("no value")).task())
                .then(checkBounds(1024))
                .then(Result.lift(this.<Integer>show("result: ")))
                .then(format())
                .start(new Task.OnFinish<Result<String>>() {
                    @Override
                    public void got(Result<String> result) {
                        c.incrementAndGet();
                        try {
                            fail("unexpected success: " + result.get());
                        } catch (IllegalStateException e) {
                            assertEquals("no value", e.getMessage());
                        } catch (Exception e) {
                            fail("unexpected exception type");
                        }
                    }
                });
        Task.Sequence
                .of(Result.ok(2048).task())
                .then(checkBounds(512))
                .then(Result.lift(this.<Integer>show("result: ")))
                .then(format())
                .start(new Task.OnFinish<Result<String>>() {
                    @Override
                    public void got(Result<String> result) {
                        c.incrementAndGet();
                        try {
                            fail("unexpected success: " + result.get());
                        } catch (IndexOutOfBoundsException e) {
                            assertEquals("max is 512", e.getMessage());
                        } catch (Exception e) {
                            fail("unexpected exception type");
                        }
                    }
                });
        Task.Sequence
                .of(Result.ok(1000).task())
                .then(Result.lift(this.<Integer>show("result: ")))
                .then(Result.lift(checkLength(10)))
                .then(format())
                .start(new Task.OnFinish<Result<String>>() {
                    @Override
                    public void got(Result<String> result) {
                        c.incrementAndGet();
                        try {
                            fail("unexpected success: " + result.get());
                        } catch (IllegalArgumentException e) {
                            assertEquals("12 is greater than 10", e.getMessage());
                        } catch (Exception e) {
                            fail("unexpected exception type");
                        }
                    }
                });
        assertEquals(4, c.get());
    }

}
