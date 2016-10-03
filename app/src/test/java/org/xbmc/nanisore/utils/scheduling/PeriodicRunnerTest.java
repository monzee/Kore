package org.xbmc.nanisore.utils.scheduling;

import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class PeriodicRunnerTest {

    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    @AfterClass
    public static void tearDownClass() {
        EXEC.shutdown();
    }

    @Test
    public void does_it_work() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(1);
        final List<Integer> sahod = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(5);
        Runner r = new PeriodicRunner(new BlockingRunner(), EXEC, 16);
        Canceller c = r.schedule(new Producer<Integer>() {
            @Override
            public Integer apply() throws Throwable {
                return counter.getAndIncrement();
            }
        }, new Continuation<Integer>() {
            @Override
            public void accept(Integer result, Throwable error) {
                sahod.add(result);
                done.countDown();
            }
        });
        done.await();
        c.cancel();
        assertThat(sahod.size(), is(5));
        assertThat(sahod, hasItems(1, 2, 3, 4, 5));
    }

    @Test(timeout = 1000)
    public void cancel_works() throws InterruptedException {
        Runner r = new PeriodicRunner(new BlockingRunner(), EXEC, 16);
        final AtomicInteger count = new AtomicInteger(0);
        final CountDownLatch done = new CountDownLatch(1);
        r.once(Task.<Void>just(null), new Continuation<Void>() {
            @Override
            public void accept(Void result, Throwable error) {
                count.getAndIncrement();
                done.countDown();
            }
        });
        done.await();
        assertEquals(1, count.get());
    }

    private static class Repeat {
        final int times;
        final int[] numbers;
        final CountDownLatch done;
        final AtomicInteger index = new AtomicInteger(0);
        int last;

        private Repeat(int times) {
            this.times = times;
            numbers = new int[times];
            done = new CountDownLatch(times);
        }

        void run(PeriodicRunner runner, Runnable then) {
            Canceller c = runner.schedule(new Producer<Integer>() {
                @Override
                public Integer apply() throws Throwable {
                    return index.getAndIncrement();
                }
            }, new Continuation<Integer>() {
                @Override
                public void accept(Integer i, Throwable error) {
                    last = i + 1;
                    numbers[i] = last;
                    done.countDown();
                }
            });
            try {
                done.await();
                c.cancel();
                then.run();
            } catch (InterruptedException e) {
                e.printStackTrace();
                c.cancel();
            }
        }
    }

    @Test
    public void initial_delay() throws InterruptedException {
        // flaky test; increase this and it might pass
        final int every = 50;
        final int ticks = 10;
        Runner r = new PeriodicRunner(new BlockingRunner(), EXEC, every * 5, every);
        final AtomicInteger count = new AtomicInteger(0);
        final Canceller c = r.schedule(new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                count.getAndIncrement();
                return null;
            }
        });
        final Repeat metronome = new Repeat(ticks);
        metronome.run(
                new PeriodicRunner(new ExecutorRunner(EXEC), EXEC, every),
                new Runnable() {
                    @Override
                    public void run() {
                        c.cancel();
                        assertThat(metronome.last, is(ticks));
                        // first run takes 5 ticks, so 4 ticks were spent doing nothing
                        assertThat(count.get(), is(ticks - 4));
                    }
                }
        );
    }

    @Test(timeout = 1000)
    public void works_with_caching_runner() {
        final Repeat r = new Repeat(5);
        MemoRunner delegate = new MemoRunner(new BlockingRunner());
        r.run(new PeriodicRunner(delegate, EXEC, 16), new Runnable() {
            @Override
            public void run() {
                assertThat(r.last, is(5));
            }
        });
    }

}
