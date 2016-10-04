package org.xbmc.nanisore.kodi;

import org.xbmc.kore.host.HostConnectionObserver;

public interface KodiEventBus {

    interface Canceller {
        void cancel();
    }

    Canceller observePlayer(HostConnectionObserver.PlayerEventsObserver observer);

}
