package org.xbmc.nanisore.kodi;

import android.content.Context;

import org.xbmc.kore.host.HostConnectionObserver;
import org.xbmc.kore.host.HostManager;

public class AndroidKodiEvents implements KodiEventBus {

    private final HostConnectionObserver events;

    public AndroidKodiEvents(Context context) {
        events = HostManager.getInstance(context).getHostConnectionObserver();
    }

    @Override
    public Canceller observePlayer(final HostConnectionObserver.PlayerEventsObserver observer) {
        events.registerPlayerObserver(observer, true);
        return new Canceller() {
            @Override
            public void cancel() {
                events.unregisterPlayerObserver(observer);
            }
        };
    }

}
