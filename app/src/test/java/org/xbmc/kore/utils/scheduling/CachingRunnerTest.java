package org.xbmc.kore.utils.scheduling;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CachingRunnerTest {

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
        Runner r = new CachingRunner(new BlockingRunner());
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
        CachingRunner runner = new CachingRunner(new BlockingRunner());
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
        CachingRunner runner = new CachingRunner(new BlockingRunner());
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
        Runner r = new CachingRunner(new BlockingRunner());
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
        CachingRunner runner = new CachingRunner(new BlockingRunner());
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

    private static <T> Producer<T> fail(final String message, T unused) {
        return new Producer<T>() {
            @Override
            public T apply() throws Throwable {
                throw new Throwable(message);
            }
        };
    }
}
