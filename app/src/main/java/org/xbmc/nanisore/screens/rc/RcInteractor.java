package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.Log;
import org.xbmc.nanisore.utils.scheduling.Canceller;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Runner;
import org.xbmc.nanisore.utils.scheduling.Task;

import java.util.HashMap;
import java.util.Map;

public class RcInteractor implements Rc.UseCases {

    private final Runner runner;
    private final Runner repeater;
    private final Map<String, Canceller> running = new HashMap<>();

    public RcInteractor(Runner runner, Runner repeater) {
        this.runner = runner;
        this.repeater = repeater;
    }

    @Override
    public void save(Rc.State state) {
        runner.schedule(Task.just("init-rc-fragment", state));
    }

    @Override
    public void restore(final Just<Rc.State> then) {
        runner.once(
                Task.just("init-rc-fragment", new Rc.State()),
                new Continuation<Rc.State>() {
                    @Override
                    public void accept(Rc.State result, Throwable error) {
                        then.got(result);
                    }
                }
        );
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
    public void connectToEventServer(Maybe<Void> then) {
    }

    @Override
    public void changeSpeed(boolean faster, Just<Integer> then) {

    }

    @Override
    public void fireAndLogTo(final Console console, final Runnable action) {
        runner.once(new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                try {
                    action.run();
                } catch (Rc.RpcError e) {
                    console.tell(e.description);
                } catch (Throwable e) {
                    e.printStackTrace();
                    Log.E.to(console, "RcInteractor action failed: %s", e);
                }
                return null;
            }
        });
    }

    @Override
    public void fireRepeatedly(String name, final Runnable action) {
        running.put(name, repeater.schedule(new Producer<Void>() {
            @Override
            public Void apply() {
                action.run();
                return null;
            }
        }));
    }

    @Override
    public void stop(String name) {
        Canceller c = running.get(name);
        if (c != null) {
            c.cancel();
            running.remove(name);
        }
    }

    @Override
    public void stop() {
        for (Canceller c : running.values()) {
            c.cancel();
        }
        running.clear();
    }
}
