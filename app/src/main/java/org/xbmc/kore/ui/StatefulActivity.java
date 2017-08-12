package org.xbmc.kore.ui;

/*
 * This file is a part of the Kore project.
 */

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;

import org.xbmc.kore.utils.LogUtils;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public abstract class StatefulActivity<
        R extends StatefulActivity<R, S>,
        S extends StatefulActivity.State<R>>
        extends BaseActivity {

    public interface State<R> {
        @MainThread
        void apply(R self);
    }

    public interface Catch {
        @MainThread
        void handle(Throwable error);
    }

    private static class Job<S extends State<?>> {
        final S producer;
        final Future<?> awaiting;

        private Job(S producer, Future<?> awaiting) {
            this.producer = producer;
            this.awaiting = awaiting;
        }

        S cancel() {
            awaiting.cancel(true);
            return producer;
        }
    }

    private static class Machine<R, S extends State<R>> {
        private static final ExecutorService AWAIT = Executors.newSingleThreadExecutor();
        private static final Handler UI = new Handler(Looper.getMainLooper());
        private static final Catch RETHROW = new Catch() {
            @Override
            public void handle(Throwable error) {
                LogUtils.LOGE(TAG, "Dispatch error", error);
                throw new RuntimeException(error);
            }
        };

        private final Queue<Job<S>> pending = new ConcurrentLinkedQueue<>();
        private final Queue<S> backlog = new ArrayDeque<>();
        private S state;

        Machine(S initialState) {
            state = initialState;
        }

        void replace(S newState) {
            state = newState;
        }

        void dispatch(final R receiver) {
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                for (S s : backlog) {
                    apply(receiver, s, RETHROW);
                }
                backlog.clear();
                apply(receiver, state, RETHROW);
            } else {
                UI.post(new Runnable() {
                    @Override
                    public void run() {
                        for (S s : backlog) {
                            apply(receiver, s, RETHROW);
                        }
                        backlog.clear();
                        apply(receiver, state, RETHROW);
                    }
                });
            }
        }

        void await(R receiver, Future<S> task) {
            await(receiver, task, RETHROW);
        }

        void await(final R receiver, final Future<S> task, final Catch errors) {
            final AtomicReference<Job<S>> job = new AtomicReference<>();
            job.set(new Job(state, AWAIT.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        replace(task.get());
                        dispatch(receiver);
                    } catch (InterruptedException ignored) {
                    } catch (final ExecutionException e) {
                        UI.post(new Runnable() {
                            @Override
                            public void run() {
                                errors.handle(e);
                            }
                        });
                    } finally {
                        pending.remove(job.get());
                        job.set(null);
                    }
                }
            })));
            pending.add(job.get());
        }

        void stop() {
            for (Job<S> job : pending) {
                backlog.add(job.cancel());
            }
            pending.clear();
        }

        private static <R, S extends State<R>> void apply(R receiver, S state, Catch errors) {
            try {
                state.apply(receiver);
            } catch (Throwable e) {
                errors.handle(e);
            }
        }
    }

    private static final String TAG = LogUtils.makeLogTag(StatefulActivity.class);

    private Machine<R, S> machine;

    protected abstract S initialState();

    public void replace(S newState) {
        machine.replace(newState);
    }

    public void apply(S newState) {
        machine.replace(newState);
        machine.dispatch((R) this);
    }

    public void dispatch() {
        machine.dispatch((R) this);
    }

    public void await(Future<S> task) {
        machine.await((R) this, task);
    }

    public void await(Future<S> task, Catch errors) {
        machine.await((R) this, task, errors);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return machine;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        machine = (Machine<R, S>) getLastCustomNonConfigurationInstance();
        if (machine == null) {
            machine = new Machine<>(initialState());
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        machine.stop();
    }
}
