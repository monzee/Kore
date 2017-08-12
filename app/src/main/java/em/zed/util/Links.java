package em.zed.util;

/*
 * This file is a part of the Kore project.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import em.zed.backend.ApiClient;

public final class Links implements ApiClient.Link {

    public static Links compose(final ApiClient.Link... links) {
        Links aggregate = new Links();
        Collections.addAll(aggregate.links, links);
        return aggregate;
    }

    private final List<ApiClient.Link> links = new ArrayList<>();
    private Links() {}

    @Override
    public void unlink() {
        for (ApiClient.Link link : links) {
            link.unlink();
        }
        links.clear();
    }
}
