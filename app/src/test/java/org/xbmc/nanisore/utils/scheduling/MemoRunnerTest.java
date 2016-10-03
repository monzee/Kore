package org.xbmc.nanisore.utils.scheduling;

import org.junit.Test;
import org.xbmc.nanisore.utils.values.Store;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MemoRunnerTest {

    private final AtomicInteger counter = new AtomicInteger(1);

    private Producer<Integer> next = new Producer<Integer>() {
        @Override
        public Integer apply() throws Throwable {
            return counter.getAndIncrement();
        }
    };

    @Test
    public void delegates_properly() throws InterruptedException {
        final CountDownLatch called = new CountDownLatch(2);
        Runner r = new MemoRunner(new BlockingRunner());
        r.schedule(new Producer<Object>() {
            @Override
            public Object apply() throws Throwable {
                called.countDown();
                return null;
            }
        }, new Continuation<Object>() {
            @Override
            public void accept(Object result, Throwable error) {
                called.countDown();
            }
        });
        assertTrue(called.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void success_value_is_cached() {
        Runner runner = new MemoRunner(new BlockingRunner());
        runner.schedule(Task.unit("foo", next), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(error);
                assertEquals(1, result.intValue());
            }
        });
        runner.schedule(Task.unit("foo", fail("", 0)), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(error);
                assertEquals(1, result.intValue());
            }
        });
    }

    @Test
    public void thrown_error_is_cached() {
        Runner runner = new MemoRunner(new BlockingRunner());
        runner.schedule(Task.unit("foo", fail("foobar", 0)), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(result);
                assertEquals("foobar", error.getMessage());
            }
        });
        runner.schedule(Task.just("foo", 1), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(result);
                assertEquals("foobar", error.getMessage());
            }
        });
    }

    @Test
    public void new_task_is_not_invoked_when_there_is_a_cached_value() throws InterruptedException {
        Runner r = new MemoRunner(new BlockingRunner());
        r.schedule(Task.just("foo", 1), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertEquals(1, result.intValue());
            }
        });
        final AtomicBoolean called = new AtomicBoolean(false);
        r.schedule(Task.unit("foo", new Producer<Object>() {
            @Override
            public Object apply() throws Throwable {
                called.set(true);
                return null;
            }
        }));
        assertFalse(called.get());
    }

    @Test
    public void cancel_clears_the_cache() {
        Runner runner = new MemoRunner(new BlockingRunner());
        runner.schedule(Task.unit("foo", next), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(error);
                assertEquals(1, result.intValue());
            }
        }).cancel();
        runner.schedule(Task.unit("foo", next), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(error);
                assertEquals(2, result.intValue());
            }
        });
        runner.schedule(Task.unit("foo", next), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertNull(error);
                assertEquals(2, result.intValue());
            }
        });
    }

    @Test
    public void put_saves_the_value() {
        CachingRunner r = new MemoRunner(new BlockingRunner());
        r.put("foo", 12345);
        r.take("foo", -1, new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertEquals(12345, result.intValue());
            }
        });
    }

    @Test
    public void take_doesnt_save() {
        CachingRunner r = new MemoRunner(new BlockingRunner());
        r.take("new", 100, new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertEquals(100, result.intValue());
            }
        });
        r.schedule(Task.just("new", -1), new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                assertEquals(-1, result.intValue());
            }
        });
    }

    @Test
    public void take_removes_saved_value() {
        CachingRunner r = new MemoRunner(new BlockingRunner());
        r.put("foo", "here");
        r.take("foo", "Asdfsdfsf", new Continuation<String>() {
            @Override
            public void accept(String result, Throwable error) {
                assertEquals("here", result);
            }
        });
        r.schedule(Task.just("foo", "not-here"), new Continuation<String>() {
            @Override
            public void accept(String result, Throwable error) {
                assertEquals("not-here", result);
            }
        });
    }

    @Test
    public void put_doesnt_overwrite() {
        CachingRunner r = new MemoRunner(new BlockingRunner());
        r.put("foo", "this");
        r.put("foo", "nope");
        r.schedule(Task.just("foo", "not this one either"), new Continuation<String>() {
            @Override
            public void accept(String result, Throwable error) {
                assertEquals("this", result);
            }
        });
    }

    @Test
    public void store_contains_all_values_stored() {
        CachingRunner r = new MemoRunner(new BlockingRunner());
        r.put("foo", "asdf");
        r.put("bar", "zxcv");
        r.put("baz", "qwer");
        Store s = r.toStore();
        assertEquals("asdf", s.get("foo", "nope"));
        assertEquals("zxcv", s.get("bar", "nope"));
        assertEquals("qwer", s.get("baz", "nope"));
        assertEquals("nope", s.get("xvlxkzjlvkj", "nope"));
        assertEquals("yes", s.hardGet("wow", "yes"));
        assertEquals("yes", s.get("wow", "nope"));
    }

    private static <T> Producer<T> fail(final String message, T unused) {
        return new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                throw new Throwable(message);
            }
        };
    }
}
