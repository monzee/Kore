package em.zed.backend;

/*
 * This file is a part of the Kore project.
 */

import org.xbmc.kore.host.HostInfo;

import java.util.List;

public interface HostLocator {
    List<HostInfo> enumerate();
    /**
     * @return null if there are no configured hosts
     */
    HostInfo preferredHost();
    ApiClient connect(HostInfo host);
}
