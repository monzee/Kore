package org.xbmc.nanisore.utils.values;

/**
 * Function interfaces
 */
public interface Do {

    interface Just<T> {
        void got(T result);
    }

    interface Try<T> {
        T get() throws Throwable;
    }

    interface Maybe<T> extends Just<Try<T>> {}

    interface Executable<T> {
        void execute(Just<T> next);
    }

    interface Step<T, U> {
        void then(T result, Just<U> next);
    }

    /**
     * Represents a step in a computation.
     *
     * Mind the thread where {@link Just#got(U)} calls are made. Changing
     * threads will cause the steps following it to be called in the new
     * thread. When you're doing an async call in an {@link #andThen(Step)},
     * make sure the callback where you call Just#got(U) is called in
     * the desired thread (the UI thread generally. In Android, this means you
     * have to post a runnable to a main looper handler).
     *
     * @param <T> Type of the value yielded by the previous step
     * @param <U> Type of the value to be yielded by this step
     */
    class Seq<T, U> implements Executable<U> {

        private static final Executable<Void> NIL = new Executable<Void>() {
            @Override
            public void execute(Just<Void> next) {
                next.got(null);
            }
        };

        private static final Just NOOP = new Just() {
            @Override
            public void got(Object result) {}
        };

        private final Executable<T> prev;
        private final Step<T, U> step;

        public Seq(Executable<T> prev, Step<T, U> step) {
            this.prev = prev;
            this.step = step;
        }

        /**
         * Start of a computation.
         *
         * Computation doesn't occur until {@link #execute()} is called.
         *
         * @param block This will be called in the same thread as the caller of
         *              execute().
         */
        public static <T> Seq<?, T> start(final Executable<T> block) {
            return new Seq<>(NIL, new Step<Void, T>() {
                @Override
                public void then(Void result, Just<T> next) {
                    block.execute(next);
                }
            });
        }

        /**
         * Continues a computation.
         */
        public <V> Seq<U, V> andThen(Step<U, V> next) {
            return new Seq<>(this, next);
        }

        /**
         * Kicks off the computation.
         *
         * Will block if none of the steps leading to this does an async call.
         * Multiple calls will re-run the computation.
         */
        public void execute(final Just<U> next) {
            prev.execute(new Just<T>() {
                @Override
                public void got(T result) {
                    step.then(result, next);
                }
            });
        }

        public void execute() {
            //noinspection unchecked
            execute(NOOP);
        }

    }

}
