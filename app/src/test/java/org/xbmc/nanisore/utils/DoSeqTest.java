package org.xbmc.nanisore.utils;

import org.junit.AfterClass;
import org.junit.Test;
import org.xbmc.nanisore.utils.values.Do;
import org.xbmc.nanisore.utils.values.Either;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DoSeqTest implements Do {

    @Test(timeout = 1000)
    public void i_swear_im_not_trying_to_recreate_rx() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        Seq.start(new Executable<String>() {
            @Override
            public void execute(Just<String> next) {
                next.got("foo");
            }
        }).andThen(new Step<String, String>() {
            @Override
            public void then(String result, Just<String> next) {
                next.got(result + "bar");
            }
        }).andThen(new Step<String, String>() {
            @Override
            public void then(String result, Just<String> next) {
                next.got(result + "baz");
            }
        }).execute(new Just<String>() {
            @Override
            public void got(String result) {
                done.countDown();
                assertEquals("foobarbaz", result);
            }
        });
        done.await();
    }

    @Test(timeout = 1000)
    public void single_step_executed_multiple_times() throws InterruptedException {
        int n = 5;
        final CountDownLatch done = new CountDownLatch(n);
        Seq<?, String> seq = Seq.start(new Executable<String>() {
            @Override
            public void execute(Just<String> next) {
                next.got("foobar");
            }
        });
        Just<String> terminal = new Just<String>() {
            @Override
            public void got(String result) {
                done.countDown();
                assertEquals("foobar", result);
            }
        };
        while (n --> 0) {
            seq.execute(terminal);
        }
        done.await();
    }

    @Test
    public void type_changing() {
        Seq.start(new Executable<String>() {
            @Override
            public void execute(Just<String> next) {
                next.got("foobarbaz");
            }
        }).andThen(new Step<String, Integer>() {
            @Override
            public void then(String result, Just<Integer> next) {
                next.got(result.length());
            }
        }).execute(new Just<Integer>() {
            @Override
            public void got(Integer result) {
                assertEquals(9, result.intValue());
            }
        });
    }

    private static ExecutorService EXEC = Executors.newSingleThreadExecutor();

    @AfterClass
    public static void tearDownClass() {
        EXEC.shutdown();
    }

    @Test(timeout = 1000)
    public void async_step_in_the_middle() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        Seq.start(new Executable<String>() {
            @Override
            public void execute(Just<String> next) {
                next.got("baz");
            }
        }).andThen(new Step<String, String>() {
            @Override
            public void then(final String result, final Just<String> next) {
                EXEC.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(50);
                            next.got("BAR" + result);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).andThen(new Step<String, String>() {
            @Override
            public void then(String result, Just<String> next) {
                next.got("foo" + result);
            }
        }).execute(new Just<String>() {
            @Override
            public void got(String result) {
                assertEquals("fooBARbaz", result);
                done.countDown();
            }
        });
        done.await();
    }

    @Test(timeout = 1000)
    public void error_handling() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        Seq.start(new Executable<Try<String>>() {
            @Override
            public void execute(Just<Try<String>> next) {
                Either<Throwable, String> either = new Either<>();
                either.left(new Throwable("hi"));
                next.got(either);
            }
        }).execute(new Maybe<String>() {
            @Override
            public void got(Try<String> result) {
                try {
                    result.get();
                    fail("should be unreachable");
                } catch (Throwable e) {
                    assertEquals("hi", e.getMessage());
                } finally {
                    done.countDown();
                }
            }
        });
        done.await();
    }

    @Test(timeout = 1000)
    public void execute_no_receiver() throws InterruptedException {
        int n = 5;
        final CountDownLatch done = new CountDownLatch(n);
        Seq<?, Void> seq = Seq.start(new Executable<Void>() {
            @Override
            public void execute(Just<Void> next) {
                done.countDown();
            }
        });
        while (n --> 0) {
            seq.execute();
        }
        done.await();
    }

    @Test(timeout = 1000)
    public void async_causes_downstream_steps_to_switch_threads() throws InterruptedException {
        final Thread mainThread = Thread.currentThread();
        final AtomicReference<Thread> bgThread = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(4);
        Seq.start(new Executable<Integer>() {
            @Override
            public void execute(Just<Integer> next) {
                assertSame(mainThread, Thread.currentThread());
                done.countDown();
                next.got(100);
            }
        }).andThen(new Step<Integer, String>() {
            @Override
            public void then(final Integer result, final Just<String> next) {
                EXEC.execute(new Runnable() {
                    @Override
                    public void run() {
                        bgThread.set(Thread.currentThread());
                        done.countDown();
                        next.got("abcde-" + result);
                    }
                });
            }
        }).andThen(new Step<String, String>() {
            @Override
            public void then(String result, Just<String> next) {
                assertSame(bgThread.get(), Thread.currentThread());
                done.countDown();
                next.got(result + "-vwxyz");
            }
        }).execute(new Just<String>() {
            @Override
            public void got(String result) {
                assertSame(bgThread.get(), Thread.currentThread());
                assertEquals("abcde-100-vwxyz", result);
                done.countDown();
            }
        });
        done.await();
    }

    @Test
    public void you_are_not_obliged_to_go_down_the_happy_path() throws InterruptedException {
        final AtomicBoolean happyPath = new AtomicBoolean();
        final AtomicInteger count = new AtomicInteger();
        Seq<?, Void> branch = Seq.start(new Executable<Boolean>() {
            @Override
            public void execute(Just<Boolean> next) {
                next.got(happyPath.get());
            }
        }).andThen(new Step<Boolean, Void>() {
            @Override
            public void then(Boolean result, Just<Void> next) {
                count.incrementAndGet();
                if (result) {
                    next.got(null);
                }
            }
        }).andThen(new Step<Void, Void>() {
            @Override
            public void then(Void result, Just<Void> next) {
                count.incrementAndGet();
                next.got(null);
            }
        });
        Just<Void> last = new Just<Void>() {
            @Override
            public void got(Void result) {
                count.incrementAndGet();
            }
        };

        happyPath.set(false);
        branch.execute(last);
        assertEquals(1, count.get());

        count.set(0);
        happyPath.set(true);
        branch.execute(last);
        assertEquals(3, count.get());
    }

}
