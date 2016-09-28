package org.xbmc.nanisore.utils.scheduling;

public class BlockingRunner extends BaseRunner {

    private static final Canceller NOOP = new Canceller() {
        @Override
        public void cancel() {
        }
    };

    @Override
    public <T> Canceller schedule(Producer<T> task, Continuation<T> handler) {
        T value = null;
        Throwable error = null;
        try {
            value = task.apply();
        } catch (Throwable e) {
            error = e;
        }
        handler.accept(value, error);
        return NOOP;
    }

}
