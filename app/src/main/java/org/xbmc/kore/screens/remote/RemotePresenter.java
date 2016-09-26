package org.xbmc.kore.screens.remote;

import org.xbmc.kore.Settings;
import org.xbmc.kore.host.HostConnectionObserver;
import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.utils.scheduling.Continuation;
import org.xbmc.kore.utils.scheduling.Producer;
import org.xbmc.kore.utils.scheduling.Runner;
import org.xbmc.kore.utils.scheduling.Task;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class RemotePresenter implements Remote.Actions, Remote.UseCases {

    private static class State implements Remote.State {
        HostConnection connection;
        HostInfo info;
        String uriToAddon;
        boolean isSharePending;
    }

    private class ReportError<T> extends Remote.MightFail<T> {
        ReportError(T callback) {
            super(callback);
        }

        @Override
        public void fail(Throwable error) {
            report(error);
            after();
        }

        protected void after() {}
    }

    private class OnPlayerEvent implements HostConnectionObserver.PlayerEventsObserver {
        @Override
        public void playerOnPlay(
                GetActivePlayersReturnType getActivePlayerResult,
                PlayerType.PropertyValue getPropertiesResult,
                ListType.ItemsAll getItemResult
        ) {

        }

        @Override
        public void playerOnPause(
                GetActivePlayersReturnType getActivePlayerResult,
                PlayerType.PropertyValue getPropertiesResult,
                ListType.ItemsAll getItemResult
        ) {

        }

        @Override
        public void playerOnStop() {

        }

        @Override
        public void playerOnConnectionError(int errorCode, String description) {

        }

        @Override
        public void playerNoResultsYet() {

        }

        @Override
        public void systemOnQuit() {

        }

        @Override
        public void inputOnInputRequested(String title, String type, String value) {

        }

        @Override
        public void observerOnStopObserving() {

        }
    };

    private Remote.Display view;
    private State state;
    private final Runner runner;
    private final Remote.Options options;
    private final Remote.Rpc rpc;
    private final Remote.MightFail<?> NOOP = new ReportError<>(null);
    private final HostManager host;
    private final HostConnectionObserver hostEvents;
    private final HostConnectionObserver.PlayerEventsObserver onPlayerEvent = new OnPlayerEvent();

    public RemotePresenter(
            HostManager host,
            Remote.Rpc rpc,
            Remote.Options options,
            Runner runner
    ) {
        this.host = host;
        this.rpc = rpc;
        this.options = options;
        this.runner = runner;
        hostEvents = host.getHostConnectionObserver();
    }

    @Override
    public void bind(Remote.Display view) {
        if (this.view != null) {
            throw new RuntimeException("Unbind first before rebinding!");
        }
        if (host.getHostInfo() == null) {
            view.goToHostAdder();
            return;
        }
        this.view = view;
        hostEvents.registerPlayerObserver(onPlayerEvent, true);
        restoreState(new ReportError<>(new Remote.OnRestore() {
            @Override
            public void restored(Remote.State state) {
                //noinspection unchecked
                RemotePresenter.this.state = (State) state;
            }
        }));
        view.initNavigationDrawer();
        view.initTabs();
        view.initActionBar();
        view.toggleKeepAboveLockScreen(isEnabled(Remote.Option.KEEP_ABOVE_LOCK_SCREEN));
        view.toggleKeepScreenOn(isEnabled(Remote.Option.KEEP_SCREEN_ON));
    }

    @Override
    public void unbind() {
        view = null;
        hostEvents.unregisterPlayerObserver(onPlayerEvent);
        saveState(state);
    }

    @Override
    public void didShareVideo(String uriString) {
        String videoUri = tryParseYoutubeUrl(uriString);
        videoUri = videoUri != null ? videoUri : tryParseVimeoUrl(uriString);
        if (videoUri == null) {
            report(Remote.Message.CANNOT_SHARE_VIDEO);
        } else if (!state.isSharePending) {
            state.isSharePending = true;
            state.uriToAddon = videoUri;
            maybeClearPlaylist(new ReportError<Remote.OnMaybeClearPlaylist>(
                    new Remote.OnMaybeClearPlaylist() {
                        @Override
                        public void playlistMaybeCleared(boolean decision) {
                            state.isSharePending = false;
                            enqueueFile(state.uriToAddon, decision, NOOP);
                        }
                    }
            ) {
                @Override
                protected void after() {
                    state.isSharePending = false;
                }
            });
        }
    }

    @Override
    public void didPressVolumeUp() {
    }

    @Override
    public void didPressVolumeDown() {
    }

    @Override
    public void didChoose(Remote.MenuAction action) {
    }

    @Override
    public void didSendText(String text, boolean done) {
    }

    @Override
    public void saveState(Remote.State state) {
        runner.schedule(Task.just("init", state));
    }

    @Override
    public void restoreState(final Remote.MightFail<? extends Remote.OnRestore> then) {
        runner.once(Task.unit("init", new Producer<State>() {
            @Override
            public State apply() throws Throwable {
                State s = new State();
                s.connection = host.getConnection();
                s.info = host.getHostInfo();
                return s;
            }
        }), new Continuation<State>() {
            @Override
            public void accept(State result, Throwable error) {
                if (error == null) {
                    then.ok.restored(result);
                } else {
                    then.fail(error);
                }
            }
        });
    }

    @Override
    public void maybeClearPlaylist(
            final Remote.MightFail<? extends Remote.OnMaybeClearPlaylist> then
    ) {
        runner.schedule(Task.unit("playlist-cleared?", new Producer<Boolean>() {
            @Override
            public Boolean apply() throws Throwable {
                for (GetActivePlayersReturnType player : rpc.getActivePlayers()) {
                    if (player.type.equals(GetActivePlayersReturnType.VIDEO)) {
                        return false;
                    }
                }
                rpc.clearPlaylist();
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
            final Remote.MightFail<?> then
    ) {
        runner.schedule(Task.unit("enqueue-" + videoUri, new Producer<Void>() {
            @Override
            public Void apply() throws Throwable {
                PlaylistType.Item item = new PlaylistType.Item();
                item.file = videoUri;
                rpc.addToPlaylist(item);
                if (startPlaylist) {
                    rpc.openPlaylist();
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

    private void report(Remote.Message msg) {
        if (view != null) {
            view.tell(msg);
        }
    }

    private void report(Throwable error) {
        if (view != null) {
            Remote.RpcError e = error instanceof Remote.RpcError ?
                (Remote.RpcError) error :
                new Remote.RpcError(
                    Remote.Message.GENERAL_ERROR,
                    0,
                    error.getLocalizedMessage()
                );
            view.tell(e.type, e.description);
        } else {
            error.printStackTrace();
        }
    }

    private boolean isEnabled(Remote.Option opt) {
        String key = null;
        boolean def = false;
        switch (opt) {
            case KEEP_ABOVE_LOCK_SCREEN:
                key = Settings.KEY_PREF_KEEP_REMOTE_ABOVE_LOCKSCREEN;
                def = Settings.DEFAULT_KEY_PREF_KEEP_REMOTE_ABOVE_LOCKSCREEN;
                break;
            case KEEP_SCREEN_ON:
                key = Settings.KEY_PREF_KEEP_SCREEN_ON;
                def = Settings.DEFAULT_KEY_PREF_KEEP_SCREEN_ON;
                break;
            case USE_HARDWARE_VOLUME_KEYS:
                key = Settings.KEY_PREF_USE_HARDWARE_VOLUME_KEYS;
                def = Settings.DEFAULT_PREF_USE_HARDWARE_VOLUME_KEYS;
                break;
        }
        return options.get(key, def);
    }

    private String tryParseVimeoUrl(String path) {
        return null;
    }

    private String tryParseYoutubeUrl(String query) {
        return null;
    }

}
