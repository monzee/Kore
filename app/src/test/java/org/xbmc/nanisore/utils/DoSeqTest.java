package org.xbmc.nanisore.utils;

import org.junit.AfterClass;
import org.junit.Test;
import org.xbmc.nanisore.utils.values.Do;
import org.xbmc.nanisore.utils.values.Either;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DoSeqTest implements Do {

    @Test(timeout = 1000)
    public void i_swear_im_not_trying_to_recreate_rx() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        Seq.start(new Init<String>() {
            @Override
            public void start(Just<String> next) {
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
        Seq<?, String> seq = Seq.start(new Init<String>() {
            @Override
            public void start(Just<String> next) {
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
        Seq.start(new Init<String>() {
            @Override
            public void start(Just<String> next) {
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
        Seq.start(new Init<String>() {
            @Override
            public void start(Just<String> next) {
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
        Seq.start(new Init<Try<String>>() {
            @Override
            public void start(Just<Try<String>> next) {
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
        Seq<?, Void> seq = Seq.start(new Init<Void>() {
            @Override
            public void start(Just<Void> next) {
                done.countDown();
            }
        });
        while (n --> 0) {
            seq.execute();
        }
        done.await();
    }

}
