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

    public static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {}
    };

    private static class State {
        HostConnection connection;
        HostInfo info;
        String uriToAddon;
        boolean isSharePending;
    }

    private Remote.Display view;
    private State state;
    private final Runner runner;
    private final Remote.Options options;
    private final Remote.Rpc rpc;
    private final HostManager host;
    private final HostConnectionObserver hostEvents;
    private final HostConnectionObserver.PlayerEventsObserver onPlayerEvent =
            new HostConnectionObserver.PlayerEventsObserver() {
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
        if (host.getHostInfo() == null) {
            view.goToHostAdder();
            return;
        }
        this.view = view;
        hostEvents.registerPlayerObserver(onPlayerEvent, true);
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
                state = result;
                if (error != null) {
                    report(error);
                }
            }
        });
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
        runner.schedule(Task.just("init", state));
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
            runner.schedule(
                    Task.unit("play-shared-video", new Producer<List<GetActivePlayersReturnType>>() {
                        @Override
                        public List<GetActivePlayersReturnType> apply() throws Throwable {
                            return rpc.getActivePlayers();
                        }
                    }).map(new Task.Transform<List<GetActivePlayersReturnType>, Boolean>() {
                        @Override
                        public Boolean apply(
                                List<GetActivePlayersReturnType> players
                        ) throws Throwable {
                            return runAndGet(clearPlaylistIfPlaying(players));
                        }
                    }).map(new Task.Transform<Boolean, Void>() {
                        @Override
                        public Void apply(Boolean start) throws Throwable {
                            return runAndGet(enqueueFile(state.uriToAddon, start));
                        }
                    }),
                    new Continuation<Void>() {
                        @Override
                        public void accept(Void result, Throwable error) {
                            state.isSharePending = false;
                            if (error != null) {
                                report(error);
                            }
                        }
                    }
            );
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
    public FutureTask<Boolean> clearPlaylistIfPlaying(
            List<GetActivePlayersReturnType> players
    ) {
        for (GetActivePlayersReturnType player : players) {
            if (player.type.equals(GetActivePlayersReturnType.VIDEO)) {
                return new FutureTask<>(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        rpc.clearPlaylist();
                        return true;
                    }
                });
            }
        }
        return new FutureTask<>(NOOP, false);
    }

    @Override
    public FutureTask<Void> enqueueFile(
            final String videoUri,
            final boolean startPlaylist
    ) {
        return new FutureTask<>(new Runnable() {
            @Override
            public void run() {
                PlaylistType.Item item = new PlaylistType.Item();
                item.file = videoUri;
                rpc.addToPlaylist(item);
                if (startPlaylist) {
                    rpc.openPlaylist();
                }
            }
        }, null);
    }

    private void report(Remote.Message msg) {
        if (view != null) {
            view.tell(msg);
        }
    }

    private void report(Throwable error) {
        if (view != null) {
            Remote.RpcError e = error instanceof Remote.RpcError
                    ? (Remote.RpcError) error
                    : new Remote.RpcError(
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

    private static <T> T runAndGet(
            FutureTask<T> future
    ) throws ExecutionException, InterruptedException {
        future.run();
        return future.get();
    }

}
