package org.xbmc.nanisore.utils.scheduling;


import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class BlockingRunnerTest {

    private static class TestRunner extends BlockingRunner {
        private final Canceller token;

        private TestRunner(Canceller token) {
            this.token = token;
        }

        @Override
        public <T> Canceller schedule(Producer<T> task, Continuation<T> handler) {
            super.schedule(task, handler);
            return token;
        }
    }

    private static class CallOrder {
        int task = -1;
        int handler = -1;
        int canceller = -1;
    }

    @Test
    public void functions_are_called_immediately() {
        final AtomicInteger c = new AtomicInteger(0);
        final CallOrder o = new CallOrder();
        Runner run = new TestRunner(new Canceller() {
            @Override
            public void cancel() {
                o.canceller = c.incrementAndGet();
            }
        });

        run.schedule(new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                o.task = c.incrementAndGet();
                return null;
            }
        }, new Continuation<Void>() {
            @Override
            public void accept(Void result, Throwable error) {
                o.handler = c.incrementAndGet();
            }
        }).cancel();

        assertEquals(1, o.task);
        assertEquals(2, o.handler);
        assertEquals(3, o.canceller);
    }

    @Test(timeout = 100)
    public void once_does_not_cause_a_deadlock() {
        final AtomicInteger c = new AtomicInteger(0);
        final CallOrder o = new CallOrder();
        Runner run = new TestRunner(new Canceller() {
            @Override
            public void cancel() {
                o.canceller = c.incrementAndGet();
            }
        });

        run.once(new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                o.task = c.incrementAndGet();
                return null;
            }
        }, new Continuation<Void>() {
            @Override
            public void accept(Void result, Throwable error) {
                o.handler = c.incrementAndGet();
            }
        });

        assertEquals(1, o.task);
        assertEquals(2, o.handler);
        assertEquals(3, o.canceller);
    }

}