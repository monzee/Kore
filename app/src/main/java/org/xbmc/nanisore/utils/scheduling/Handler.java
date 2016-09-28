package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.Try;

public interface Handler<T> {
    void then(Try<T> result);
}
