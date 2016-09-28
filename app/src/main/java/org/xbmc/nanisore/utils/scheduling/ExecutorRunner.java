package org.xbmc.nanisore.utils.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ExecutorRunner extends BaseRunner {

    private final ExecutorService taskExecutor;
    private final Executor handlerExecutor;

    public ExecutorRunner(ExecutorService task) {
        this(task, null);
    }

    public ExecutorRunner(ExecutorService task, Executor handler) {
        taskExecutor = task;
        handlerExecutor = handler;
    }

    @Override
    public <T> Canceller schedule(final Producer<T> task, final Continuation<T> handler) {
        final Future<?> future = taskExecutor.submit(new Runnable() {
            @Override
            public void run() {
                T value = null;
                Throwable error = null;
                try {
                    value = task.apply();
                } catch (Throwable e) {
                    error = e;
                }
                if (handlerExecutor == null) {
                    handler.accept(value, error);
                } else {
                    final Task.Result<T> result = new Task.Result<>(value, error);
                    handlerExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            handler.accept(result.value, result.error);
                        }
                    });
                }
            }
        });
        return new Canceller() {
            @Override
            public void cancel() {
                future.cancel(true);
            }
        };
    }

}
