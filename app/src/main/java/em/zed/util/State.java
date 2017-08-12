package em.zed.util;

/*
 * This file is a part of the Kore project.
 */

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Types for modelling an interactive async system using a Moore state machine.
 */
public interface State {

    /**
     * An action causes the system's state to change when applied.
     * <p>
     * This along with its type parameter implement the double dispatch pattern.
     * In functional terms, {@link Action} is a sum type whose branches are the
     * methods of the associated type. Instances of the associated type (the
     * "actor") are like single-level patterns with a ton of ceremony because
     * java and no return value, just side effects.
     * <p>
     * Action instances are also referred to as "state" in this document. The
     * actual state is the arguments passed to the actor method being called
     * in the action. In a strict Moore machine implementation, the
     * {@link #apply} will only call a single actor method once, so the action
     * is effectively the state itself. In the "passive view" or "humble object"
     * pattern however, the actor methods are more granular; the {@link #apply}
     * method bodies generally contain more logic and invoke multiple actor
     * methods, thus the actual state cannot be precisely identified.
     * Nevertheless, applying the action should bring the system to the desired
     * state so the distinction doesn't matter so much.
     *
     * @param <E> the concrete actor type
     */
    interface Action<E> {
        /**
         * Implementations of this method should only call a single method of
         * the actor once and do nothing else. This cannot be enforced by the
         * language so diligence is required to keep the system sane.
         *
         * @param actor an object that "renders" the resulting state of the
         *              system.
         */
        void apply(E actor);
    }

    /**
     * Hook to handle uncaught exceptions thrown by an actor or a future action.
     */
    interface Catch {
        void handle(Throwable error);
    }

    /**
     * Provides a thread context to actions and the fallback exception handler.
     */
    interface Dispatcher extends Catch {
        void run(Runnable block);
    }

    class Machine<A extends Action<E>, E> {

        private final ExecutorService joinContext;
        private final Dispatcher dispatcher;
        private final Queue<Job<A>> pending = new ArrayDeque<>();
        private A lastAction;

        public Machine(ExecutorService joinContext, Dispatcher dispatcher) {
            this.joinContext = joinContext;
            this.dispatcher = dispatcher;
        }

        /**
         * @return the last action applied. Useful to set a checkpoint of sorts
         * that you can return to later.
         */
        public A peek() {
            return lastAction;
        }

        /**
         * Applies an action.
         * <p>
         * Shortcut to {@link #willApply(Action)} followed by
         * {@link #dispatch(Object)}.
         *
         * @param action to apply
         * @param actor will receive the new state
         */
        public void apply(A action, E actor) {
            if (willApply(action)) {
                dispatch(actor);
            }
        }

        /**
         * Schedules an action to be applied on the next call to
         * {@link #dispatch(Object)}.
         *
         * @param action to apply
         * @return whether the action was accepted. Meant to be overridden;
         * default implementation always accepts.
         */
        public boolean willApply(A action) {
            lastAction = action;
            return true;
        }

        /**
         * Applies the last action.
         *
         * @param actor will receive the new state.
         */
        public void dispatch(final E actor) {
            if (lastAction == null) {
                return;
            }
            dispatcher.run(new Runnable() {
                @Override
                public void run() {
                    try {
                        lastAction.apply(actor);
                    } catch (Throwable e) {
                        dispatcher.handle(e);
                    }
                }
            });
        }

        public void await(Future<A> task, E actor) {
            await(task, actor, dispatcher);
        }

        public void await(final Future<A> task, final E actor, final Catch errors) {
            final AtomicReference<Job<A>> job = new AtomicReference<>();
            job.set(new Job<>(lastAction, joinContext.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        apply(task.get(), actor);
                    } catch (final ExecutionException e) {
                        dispatcher.run(new Runnable() {
                            @Override
                            public void run() {
                                errors.handle(e);
                            }
                        });
                    } catch (InterruptedException ignored) {
                    } finally {
                        pending.remove(job.get());
                        job.set(null);
                    }
                }
            })));
            pending.add(job.get());
        }

        public Queue<A> stop() {
            Queue<A> backlog = new ArrayDeque<>();
            for (Job<A> job : pending) {
                backlog.add(job.cancel());
            }
            pending.clear();
            return backlog;
        }

        private static class Job<A extends Action<?>> {
            final A producer;
            final Future<?> await;

            Job(A producer, Future<?> await) {
                this.producer = producer;
                this.await = await;
            }

            A cancel() {
                await.cancel(true);
                return producer;
            }
        }
    }
}
