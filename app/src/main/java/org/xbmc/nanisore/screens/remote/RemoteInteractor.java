package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.MightFail;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Runner;
import org.xbmc.nanisore.utils.scheduling.Task;

public class RemoteInteractor implements Remote.UseCases {

    public static final class Builder {
        private final Runner runner;
        private final Remote.Rpc rpc;
        private final Remote.State state = new Remote.State();

        public Builder(Runner runner, Remote.Rpc rpc) {
            this.runner = runner;
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
            return new RemoteInteractor(runner, rpc, state);
        }
    }

    private final Runner runner;
    private final Remote.Rpc rpc;
    private final Remote.State initState;

    RemoteInteractor(Runner runner, Remote.Rpc rpc, Remote.State state) {
        this.runner = runner;
        this.rpc = rpc;
        this.initState = state;
    }

    @Override
    public void saveState(Remote.State state) {
        runner.schedule(Task.just("init", state));
    }

    @Override
    public void restoreState(final Remote.OnRestore then) {
        runner.once(Task.just("init", initState), new Continuation<Remote.State>() {
            @Override
            public void accept(Remote.State result, Throwable error) {
                then.restored(result);
            }
        });
    }

    @Override
    public void maybeClearPlaylist(
            final MightFail<? extends Remote.OnMaybeClearPlaylist> then
    ) {
        runner.schedule(Task.unit("playlist-cleared?", new Producer<Boolean>() {
            @Override
            public Boolean apply() throws InterruptedException {
                for (PlayerType.GetActivePlayersReturnType player : rpc.tryGetActivePlayers()) {
                    if (player.type.equals(PlayerType.GetActivePlayersReturnType.VIDEO)) {
                        return false;
                    }
                }
                rpc.tryClearVideoPlaylist();
                return true;
            }
        }), new Continuation<Boolean>() {
            @Override
            public void accept(Boolean result, Throwable error) {
                if (error == null) {
                    then.ok.playlistMaybeCleared(result);
                } else {
                    then.fail(error);
                }
            }
        });
    }

    @Override
    public void enqueueFile(
            final String videoUri,
            final boolean startPlaylist,
            final MightFail<? extends Runnable> then
    ) {
        runner.schedule(Task.unit("enqueue-" + videoUri, new Producer<Void>() {
            @Override
            public Void apply() throws InterruptedException {
                PlaylistType.Item item = new PlaylistType.Item();
                item.file = videoUri;
                rpc.tryAddToVideoPlaylist(item);
                if (startPlaylist) {
                    rpc.tryOpenVideoPlaylist();
                }
                return null;
            }
        }), new Continuation<Void>() {
            @Override
            public void accept(Void result, Throwable error) {
                if (error == null) {
                    then.ok.run();
                } else {
                    then.fail(error);
                }
            }
        });
    }

    @Override
    public void fireAndForget(final Runnable action) {
        runner.once(new Producer<Void>() {
            @Override
            public Void apply() {
                action.run();
                return null;
            }
        });
    }

}
