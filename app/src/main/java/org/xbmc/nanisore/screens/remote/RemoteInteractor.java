package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.scheduling.CachingRunner;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Task;

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
    public void restore(final Just<Remote.State> then) {
        cache.take("init", initState, new Continuation<Remote.State>() {
            @Override
            public void accept(Remote.State result, Throwable error) {
                then.got(result);
            }
        });
    }

    @Override
    public void maybeClearPlaylist(final Maybe<Boolean> then) {
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
            final Maybe<Void> then
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

}
