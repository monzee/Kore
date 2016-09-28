package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.Settings;
import org.xbmc.kore.host.HostConnectionObserver;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.nanisore.utils.Lazy;
import org.xbmc.nanisore.utils.Log;
import org.xbmc.nanisore.utils.MightFail;
import org.xbmc.nanisore.utils.Options;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemotePresenter implements Remote.Actions {

    private class ReportError<T> extends MightFail<T> {
        ReportError(T callback) {
            super(callback);
        }

        @Override
        public void fail(Throwable error) {
            report(error);
        }
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
    }

    private final Lazy<Boolean> useVolumeKeys = new Lazy<Boolean>() {
        @Override
        protected Boolean value() {
            return isEnabled(Remote.Option.USE_HARDWARE_VOLUME_KEYS);
        }
    };

    private Remote.Ui view;
    private Remote.State state;
    private final Options options;
    private final Remote.UseCases will;
    private final Remote.Rpc rpc;
    private final HostManager host;
    private final HostConnectionObserver hostEvents;
    private final HostConnectionObserver.PlayerEventsObserver onPlayerEvent = new OnPlayerEvent();

    public RemotePresenter(
            HostManager host,
            Remote.UseCases will,
            Remote.Rpc rpc,
            Options options
    ) {
        this.host = host;
        this.will = will;
        this.rpc = rpc;
        this.options = options;
        hostEvents = host.getHostConnectionObserver();
    }

    @Override
    public void bind(final Remote.Ui view) {
        if (this.view != null) {
            throw new RuntimeException("Unbind first before rebinding!");
        }
        if (host.getHostInfo() == null) {
            view.goToHostAdder();
            return;
        }
        this.view = view;
        hostEvents.registerPlayerObserver(onPlayerEvent, true);
        will.restoreState(new Remote.OnRestore() {
            @Override
            public void restored(Remote.State state) {
                RemotePresenter.this.state = state;
                view.initNavigationDrawer();
                view.initTabs(state.activeTab);
                view.initActionBar();
                view.toggleKeepAboveLockScreen(isEnabled(Remote.Option.KEEP_ABOVE_LOCK_SCREEN));
                view.toggleKeepScreenOn(isEnabled(Remote.Option.KEEP_SCREEN_ON));
            }
        });
    }

    @Override
    public void unbind() {
        view = null;
        if (hostEvents != null) {
            hostEvents.unregisterPlayerObserver(onPlayerEvent);
            rpc.dispose();
            will.saveState(state);
        }
    }

    @Override
    public void didShareVideo(String uriString) {
        Log.I.to(view, "attempting to share: %s", uriString);
        try {
            URL url = new URL(uriString);
            String videoUri = tryParseYoutubeUrl(url);
            videoUri = videoUri != null ? videoUri : tryParseVimeoUrl(url);
            if (videoUri == null) {
                report(Remote.Message.CANNOT_SHARE_VIDEO);
            } else {
                Log.I.to(view, "attempting to enqueue: %s", videoUri);
                final String uriToAddon = videoUri;
                will.maybeClearPlaylist(new ReportError<>(new Remote.OnMaybeClearPlaylist() {
                    @Override
                    public void playlistMaybeCleared(boolean wasCleared) {
                        will.enqueueFile(uriToAddon, wasCleared, new ReportError<>(null));
                    }
                }));
            }
        } catch (MalformedURLException e) {
            Log.E.to(view, "failed to parse video url: %s", uriString);
            report(Remote.Message.CANNOT_SHARE_VIDEO);
        }
    }

    @Override
    public void didPressVolumeUp() {
        if (useVolumeKeys.get()) {
            will.fireAndForget(new Runnable() {
                @Override
                public void run() {
                    rpc.increaseVolume();
                }
            });
        }
    }

    @Override
    public void didPressVolumeDown() {
        if (useVolumeKeys.get()) {
            will.fireAndForget(new Runnable() {
                @Override
                public void run() {
                    rpc.decreaseVolume();
                }
            });
        }
    }

    @Override
    public void didChoose(final Remote.MenuAction action) {
        will.fireAndForget(new Runnable() {
            @Override
            public void run() {
                switch (action) {
                    case WAKE_UP:
                        break;
                    case QUIT:
                        break;
                    case SUSPEND:
                        break;
                    case REBOOT:
                        break;
                    case SHUTDOWN:
                        break;
                    case SEND_TEXT:
                        break;
                    case FULLSCREEN:
                        break;
                    case CLEAN_VIDEO_LIBRARY:
                        break;
                    case CLEAN_AUDIO_LIBRARY:
                        break;
                    case UPDATE_VIDEO_LIBRARY:
                        break;
                    case UPDATE_AUDIO_LIBRARY:
                        break;
                }
            }
        });
    }

    @Override
    public void didSendText(String text, boolean done) {
    }

    @Override
    public void didSwitchTab(int position) {
        state.activeTab = position;
    }

    private void report(Remote.Message msg, Object... fmtArgs) {
        if (view != null) {
            view.tell(msg, fmtArgs);
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

    private String tryParseVimeoUrl(URL url) {
        String host = url.getHost();
        if (host == null || !host.endsWith("vimeo.com")) {
            return null;
        }
        return pluginUriFromPath(url, "vimeo");
    }

    private String tryParseYoutubeUrl(URL url) {
        String host = url.getHost();
        if (host == null || !(host.endsWith("youtube.com") || host.endsWith("youtu.be"))) {
            return null;
        }
        if (host.endsWith("youtu.be")) {
            return pluginUriFromPath(url, "youtube");
        } else {
            String query = url.getQuery();
            if (query == null) {
                return null;
            }
            Matcher getVideoId = Pattern.compile("(?:^|&)v=([^&]+)").matcher(query);
            if (!getVideoId.find()) {
                return null;
            }
            return "plugin://plugin.video.youtube/play/?video_id=" + getVideoId.group(1);
        }
    }

    private String pluginUriFromPath(URL url, String pluginName) {
        String path = url.getPath();
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
