package org.xbmc.nanisore.utils;

import org.junit.AfterClass;
import org.junit.Test;
import org.xbmc.nanisore.utils.values.Either;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EitherTest {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    @AfterClass
    public static void tearDownClass() {
        EXEC.shutdown();
    }

    @Test
    public void immediate_get() throws Throwable {
        Either<?, String> either = new Either<>();
        either.right("foo");
        assertEquals("foo", either.get());
    }

    @Test(expected = RuntimeException.class)
    public void immediate_throw() throws InterruptedException {
        Either<RuntimeException, ?> either = new Either<>();
        either.left(new RuntimeException());
        either.get();
    }

    @Test
    public void does_not_throw_if_value_is_already_set() throws InterruptedException {
        Either<RuntimeException, String> either = new Either<>();
        either.right("foo");
        either.left(new RuntimeException());
        assertEquals("foo", either.get());
    }

    @Test(expected = RuntimeException.class)
    public void does_not_set_the_value_if_error_is_already_set() throws InterruptedException {
        Either<RuntimeException, String> either = new Either<>();
        either.left(new RuntimeException());
        either.right("foo");
        either.get();
    }

    @Test
    public void does_not_overwrite_the_value() throws Throwable {
        Either<?, String> either = new Either<>();
        either.right("foo");
        either.right("bar");
        assertEquals("foo", either.get());
    }

    @Test
    public void does_not_overwrite_the_error() throws InterruptedException {
        Either<RuntimeException, ?> either = new Either<>();
        either.left(new RuntimeException("foo"));
        either.left(new RuntimeException("bar"));
        try {
            either.get();
        } catch (RuntimeException e) {
            assertEquals("foo", e.getMessage());
        }
    }

    @Test
    public void get_blocks_until_value_is_set() throws Throwable {
        final Either<?, String> either = new Either<>();
        final Either<?, String> mirror = new Either<>();
        final CountDownLatch signal = new CountDownLatch(1);
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mirror.right(either.get());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    fail("interrupted");
                } finally {
                    signal.countDown();
                }
            }
        });
        assertFalse(signal.await(50, TimeUnit.MILLISECONDS));
        either.right("foo");
        assertTrue(signal.await(50, TimeUnit.MILLISECONDS));
        assertEquals("foo", mirror.get());
    }

    @Test(expected = RuntimeException.class)
    public void get_blocks_until_the_error_is_set() throws InterruptedException {
        final Either<RuntimeException, ?> either = new Either<>();
        final Either<RuntimeException, ?> mirror = new Either<>();
        final CountDownLatch signal = new CountDownLatch(1);
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    either.get();
                } catch (RuntimeException e) {
                    mirror.left(e);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail("interrupted");
                } finally {
                    signal.countDown();
                }
            }
        });
        assertFalse(signal.await(50, TimeUnit.MILLISECONDS));
        either.left(new RuntimeException("asdfasdf"));
        assertTrue(signal.await(50, TimeUnit.MILLISECONDS));
        mirror.get();
    }

}