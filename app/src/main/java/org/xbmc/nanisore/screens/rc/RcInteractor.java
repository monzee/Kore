package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.Log;
import org.xbmc.nanisore.utils.scheduling.CachingRunner;
import org.xbmc.nanisore.utils.scheduling.Canceller;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Runner;
import org.xbmc.nanisore.utils.values.Do;

import java.util.HashMap;
import java.util.Map;

public class RcInteractor implements Rc.UseCases {

    private final CachingRunner cache;
    private final Runner repeater;
    private final Map<String, Canceller> running = new HashMap<>();

    public RcInteractor(CachingRunner cache, Runner repeater) {
        this.cache = cache;
        this.repeater = repeater;
    }

    @Override
    public void save(Rc.State state) {
        cache.put("init-rc-fragment", state);
    }

    @Override
    public void restore(final Do.Just<Rc.State> then) {
        cache.take("init-rc-fragment", new Rc.State(), new Continuation<Rc.State>() {
            @Override
            public void accept(Rc.State result, Throwable error) {
                then.got(result);
            }
        });
    }

    @Override
    public void fireAndForget(final Runnable action) {
        cache.once(new Producer<Void>() {
            @Override
            public Void apply() {
                action.run();
                return null;
            }
        });
    }

    @Override
    public void connectToEventServer(Do.Maybe<Void> then) {
    }

    @Override
    public void changeSpeed(boolean faster, Do.Just<Integer> then) {

    }

    @Override
    public void fireAndLogTo(final Console console, final Runnable action) {
        cache.once(new Producer<Void>() {
            @Override
            public Void apply() {
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
