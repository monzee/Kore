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

    interface Init<T> {
        void start(Just<T> next);
    }

    interface Step<T, U> {
        void then(T result, Just<U> next);
    }

    interface Executable<T> {
        void execute(Just<T> block);
    }

    class Seq<T, U> implements Executable<U> {

        private static final Executable<Void> NIL = new Executable<Void>() {
            @Override
            public void execute(Just<Void> block) {
                block.got(null);
            }
        };

        private final Just<U> NOOP = new Just<U>() {
            @Override
            public void got(U result) {}
        };

        private final Executable<T> prev;
        private final Step<T, U> step;

        public Seq(Executable<T> prev, Step<T, U> step) {
            this.prev = prev;
            this.step = step;
        }

        public static <T> Seq<?, T> start(final Init<T> block) {
            return new Seq<>(NIL, new Step<Void, T>() {
                @Override
                public void then(Void result, Just<T> next) {
                    block.start(next);
                }
            });
        }

        public <V> Seq<U, V> andThen(Step<U, V> next) {
            return new Seq<>(this, next);
        }

        public void execute(final Just<U> block) {
            prev.execute(new Just<T>() {
                @Override
                public void got(T result) {
                    step.then(result, block);
                }
            });
        }

        public void execute() {
            execute(NOOP);
        }

    }

}
