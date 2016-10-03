package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.Settings;
import org.xbmc.kore.host.HostConnectionObserver;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Log;
import org.xbmc.nanisore.utils.Options;
import org.xbmc.nanisore.utils.values.Lazy;
import org.xbmc.nanisore.utils.values.Try;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemotePresenter implements Remote.Actions {

    private class OnPlayerEvent implements HostConnectionObserver.PlayerEventsObserver {
        @Override
        public void playerOnPlay(
                GetActivePlayersReturnType getActivePlayerResult,
                PlayerType.PropertyValue getPropertiesResult,
                ListType.ItemsAll getItemResult
        ) {
            if (view == null) {
                return;
            }
            String img = getItemResult.fanart;
            if (img == null || img.trim().isEmpty()) {
                img = getItemResult.thumbnail;
            }
            if (img != null && !img.trim().isEmpty() && !img.equals(state.imageUrl)) {
                view.setBackgroundImage(img);
                state.imageUrl = img;
            }
            if (!state.isObservingPlayer) {
                view.startObservingPlayerStatus();
                state.isObservingPlayer = true;
            }
        }

        @Override
        public void playerOnPause(
                GetActivePlayersReturnType getActivePlayerResult,
                PlayerType.PropertyValue getPropertiesResult,
                ListType.ItemsAll getItemResult
        ) {
            playerOnPlay(getActivePlayerResult, getPropertiesResult, getItemResult);
        }

        @Override
        public void playerOnStop() {
            if (state.imageUrl != null && view != null) {
                view.setBackgroundImage(null);
                state.imageUrl = null;
            }
        }

        @Override
        public void playerOnConnectionError(int errorCode, String description) {
            playerOnStop();
        }

        @Override
        public void playerNoResultsYet() {
            // noop
        }

        @Override
        public void systemOnQuit() {
            report(Remote.Message.IS_QUITTING);
            playerOnStop();
        }

        @Override
        public void inputOnInputRequested(String title, String type, String value) {
            if (view != null) {
                view.promptTextToSend(title);
            }
        }

        @Override
        public void observerOnStopObserving() {
            // noop
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
    private final Remote.Rpc kodi;
    private final HostConnectionObserver hostEvents;
    private final HostConnectionObserver.PlayerEventsObserver onPlayerEvent = new OnPlayerEvent();

    public RemotePresenter(
            Remote.UseCases will,
            Remote.Rpc kodi,
            Options options,
            HostConnectionObserver hostEvents
    ) {
        this.will = will;
        this.kodi = kodi;
        this.options = options;
        this.hostEvents = hostEvents;
    }

    @Override
    public void bind(final Remote.Ui view) {
        if (this.view != null) {
            throw new RuntimeException("Unbind first before rebinding!");
        }
        will.restore(new Conventions.Just<Remote.State>() {
            @Override
            public void got(Remote.State state) {
                if (!state.isHostPresent) {
                    view.goToHostAdder();
                    return;
                }
                RemotePresenter.this.view = view;
                RemotePresenter.this.state = state;
                hostEvents.registerPlayerObserver(onPlayerEvent, true);
                view.initNavigationDrawer();
                view.initTabs(state.fresh);
                view.initActionBar();
                view.toggleKeepAboveLockScreen(isEnabled(Remote.Option.KEEP_ABOVE_LOCK_SCREEN));
                view.toggleKeepScreenOn(isEnabled(Remote.Option.KEEP_SCREEN_ON));
                if (state.videoToShare != null) {
                    didShareVideo(state.videoToShare);
                }
                if (state.imageUrl != null) {
                    view.setBackgroundImage(state.imageUrl);
                }
                if (state.isObservingPlayer) {
                    view.startObservingPlayerStatus();
                }
                state.fresh = false;
            }
        });
    }

    @Override
    public void unbind() {
        view = null;
        kodi.dispose();
        if (state != null) {
            hostEvents.unregisterPlayerObserver(onPlayerEvent);
            will.save(state);
        }
    }

    @Override
    public void didShareVideo(String uriString) {
        try {
            URL url = new URL(uriString);
            String videoUri = parseYouTubeId(url);
            videoUri = videoUri != null ? videoUri : parseVimeoId(url);
            if (videoUri == null) {
                Log.I.to(view, "can't recognize share: %s", uriString);
                report(Remote.Message.CANNOT_SHARE_VIDEO);
            } else {
                Log.I.to(view, "attempting to enqueue: %s", videoUri);
                final String uriToAddon = videoUri;
                will.maybeClearPlaylist(report(new Conventions.Just<Boolean>() {
                    @Override
                    public void got(Boolean result) {
                        will.enqueueFile(uriToAddon, result, report(new Conventions.Just<Void>() {
                            @Override
                            public void got(Void ignored) {
                                state.videoToShare = null;
                                if (view != null) {
                                    view.refreshPlaylist();
                                }
                            }
                        }));
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
            kodi.increaseVolume();
        }
    }

    @Override
    public void didPressVolumeDown() {
        if (useVolumeKeys.get()) {
            kodi.decreaseVolume();
        }
    }

    @Override
    public void didChoose(final Remote.Menu action) {
        switch (action) {
            case WAKE_UP:
                will.fireAndForget(new Runnable() {
                    @Override
                    public void run() {
                        kodi.tryWakeUp();
                    }
                });
                break;
            case QUIT:
                kodi.quit();
                break;
            case SUSPEND:
                kodi.suspend();
                break;
            case REBOOT:
                kodi.reboot();
                break;
            case SHUTDOWN:
                kodi.shutdown();
                break;
            case SEND_TEXT:
                if (view != null) {
                    view.promptTextToSend();
                }
                break;
            case FULLSCREEN:
                kodi.toggleFullScreen();
                break;
            case CLEAN_VIDEO_LIBRARY:
                kodi.cleanVideoLibrary();
                break;
            case CLEAN_AUDIO_LIBRARY:
                kodi.cleanAudioLibrary();
                break;
            case UPDATE_VIDEO_LIBRARY:
                kodi.updateVideoLibrary();
                break;
            case UPDATE_AUDIO_LIBRARY:
                kodi.updateAudioLibrary();
                break;
        }
    }

    @Override
    public void didSendText(String text, boolean done) {
        kodi.sendText(text, done);
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

    private <T> Conventions.Maybe<T> report(final Conventions.Just<T> handler) {
        return new Conventions.Maybe<T>() {
            @Override
            public void got(Try<T> result) {
                try {
                    handler.got(result.get());
                } catch (Throwable e) {
                    report(e);
                }
            }
        };
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
