package org.xbmc.nanisore.screens.rc;

import org.xbmc.kore.utils.UIUtils;
import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.MightFail;
import org.xbmc.nanisore.utils.scheduling.BlockingRunner;
import org.xbmc.nanisore.utils.scheduling.Canceller;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.PeriodicRunner;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Runner;
import org.xbmc.nanisore.utils.scheduling.Task;

import java.util.HashMap;
import java.util.Map;

public class RcInteractor implements Rc.UseCases {

    private final Runner runner;
    private final Runner repeater = new PeriodicRunner(
            new BlockingRunner(),
            UIUtils.initialButtonRepeatInterval,
            UIUtils.buttonRepeatInterval
    );
    private final Map<String, Canceller> running = new HashMap<>();

    public RcInteractor(Runner runner) {
        this.runner = runner;
    }

    @Override
    public void save(Rc.State state) {
        runner.schedule(Task.just("init-rc-fragment", state));
    }

    @Override
    public void restore(final OnRestore<Rc.State> then) {
        runner.once(
                Task.just("init-rc-fragment", new Rc.State()),
                new Continuation<Rc.State>() {
                    @Override
                    public void accept(Rc.State result, Throwable error) {
                        then.restored(result);
                    }
                }
        );
    }

    @Override
    public void connectToEventServer(MightFail<?> then) {

    }

    @Override
    public void changeSpeed(boolean faster, Rc.OnSpeedChange then) {

    }

    @Override
    public void fireAndLogTo(Console console, Runnable action) {

    }

    @Override
    public void fireAndForget(final Runnable action) {
        runner.once(new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                action.run();
                return null;
            }
        });
    }

    @Override
    public void fireAndFireAndFire(String name, final Runnable action) {
        running.put(name, repeater.schedule(Task.unit(name, new Producer<Void>() {
            @Override
            public Void apply() {
                action.run();
                return null;
            }
        })));
    }

    @Override
    public void stop(String name) {
        Canceller c = running.get(name);
        if (c != null) {
            c.cancel();
        }
    }

    @Override
    public void stop() {
        for (String key : running.keySet()) {
            running.get(key).cancel();
        }
    }
}
