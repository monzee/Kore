package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.scheduling.CachingRunner;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Task;
import org.xbmc.nanisore.utils.values.Do;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteInteractor implements Remote.UseCases {

    public static final class Builder {
        private final CachingRunner cache;
        private final Remote.Rpc rpc;
        private final Remote.State state = new Remote.State();

        public Builder(CachingRunner cache, Remote.Rpc rpc) {
            this.cache = cache;
            this.rpc = rpc;
        }

        public Builder withVideoToShare(String uri) {
            state.videoToShare = uri;
            return this;
        }

        public Builder withHostPresent(boolean present) {
            state.isHostPresent = present;
            return this;
        }

        public RemoteInteractor build() {
            return new RemoteInteractor(cache, rpc, state);
        }
    }

    private final CachingRunner cache;
    private final Remote.Rpc kodi;
    private final Remote.State initState;

    RemoteInteractor(CachingRunner cache, Remote.Rpc kodi, Remote.State state) {
        this.cache = cache;
        this.kodi = kodi;
        this.initState = state;
    }

    @Override
    public void save(Remote.State state) {
        cache.put("init", state);
    }

    @Override
    public void restore(final Do.Just<Remote.State> then) {
        cache.take("init", initState, new Continuation<Remote.State>() {
            @Override
            public void accept(Remote.State result, Throwable error) {
                then.got(result);
            }
        });
    }

    @Override
    public String transformUrlToKodiPluginUrl(String videoUrl) throws MalformedURLException {
        URL url = new URL(videoUrl);
        String fromYouTube = parseYouTubeId(url);
        return fromYouTube != null ? fromYouTube : parseVimeoId(url);
    }

    @Override
    public void maybeClearPlaylist(final Do.Maybe<Boolean> then) {
        cache.schedule(Task.unit("playlist-cleared?", new Producer<Boolean>() {
            @Override
            public Boolean apply() throws Throwable {
                for (GetActivePlayersReturnType player : kodi.tryGetActivePlayers()) {
                    if (player.type.equals(GetActivePlayersReturnType.VIDEO)) {
                        return false;
                    }
                }
                kodi.tryClearVideoPlaylist();
                return true;
            }
        }), then);
    }

    @Override
    public void enqueueFile(
            final String videoUri,
            final boolean startPlaylist,
            final Do.Maybe<Void> then
    ) {
        cache.schedule(Task.unit("enqueue-" + videoUri, new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                PlaylistType.Item item = new PlaylistType.Item();
                item.file = videoUri;
                kodi.tryAddToVideoPlaylist(item);
                if (startPlaylist) {
                    kodi.tryOpenVideoPlaylist();
                }
                return null;
            }
        }), then);
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

    private String parseVimeoId(URL url) {
        String host = url.getHost();
        if (host == null || !host.endsWith("vimeo.com")) {
            return null;
        }
        return pluginUriFromPath(url.getPath(), "vimeo");
    }

    private String parseYouTubeId(URL url) {
        String host = url.getHost();
        if (host == null || !(host.endsWith("youtube.com") || host.endsWith("youtu.be"))) {
            return null;
        }
        String path = url.getPath();
        if (host.endsWith("youtube.com")) {
            String query = url.getQuery();
            if (query == null) {
                return null;
            }
            Matcher getVideoId = Pattern.compile("(?:^|&)v=([^&]+)").matcher(query);
            if (!getVideoId.find()) {
                return null;
            }
            path = "/" + getVideoId.group(1);
        }
        return pluginUriFromPath(path, "youtube");
    }

    private String pluginUriFromPath(String path, String pluginName) {
        if (path == null || path.length() < 1) {
            return null;
        }
        return String.format(
                "plugin://plugin.video.%s/play/?video_id=%s",
                pluginName,
                path.substring(1).split("/", 2)[0]
        );
    }

}
