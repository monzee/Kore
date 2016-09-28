package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.MightFail;
import org.xbmc.nanisore.utils.scheduling.Continuation;
import org.xbmc.nanisore.utils.scheduling.Producer;
import org.xbmc.nanisore.utils.scheduling.Runner;
import org.xbmc.nanisore.utils.scheduling.Task;

public class RemoteInteractor implements Remote.UseCases {

    private final Runner runner;
    private final Remote.Rpc rpc;

    public RemoteInteractor(Runner runner, Remote.Rpc rpc) {
        this.runner = runner;
        this.rpc = rpc;
    }

    @Override
    public void saveState(Remote.State state) {
        runner.schedule(Task.just("init", state));
    }

    @Override
    public void restoreState(final Remote.OnRestore then) {
        runner.once(Task.unit("init", new Producer<Remote.State>() {
            @Override
            public Remote.State apply() {
                Remote.State s = new Remote.State();
                s.activeTab = 1;
                s.sharedVideoEnqueued = false;
                return s;
            }
        }), new Continuation<Remote.State>() {
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
                for (PlayerType.GetActivePlayersReturnType player : rpc.getActivePlayers()) {
                    if (player.type.equals(PlayerType.GetActivePlayersReturnType.VIDEO)) {
                        return false;
                    }
                }
                rpc.clearVideoPlaylist();
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
            final MightFail<?> then
    ) {
        runner.schedule(Task.unit("enqueue-" + videoUri, new Producer<Void>() {
            @Override
            public Void apply() throws InterruptedException {
                PlaylistType.Item item = new PlaylistType.Item();
                item.file = videoUri;
                rpc.addToVideoPlaylist(item);
                if (startPlaylist) {
                    rpc.openVideoPlaylist();
                }
                return null;
            }
        }), new Continuation<Void>() {
            @Override
            public void accept(Void result, Throwable error) {
                if (error != null) {
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
