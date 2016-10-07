package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.Settings;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.nanisore.utils.Log;
import org.xbmc.nanisore.utils.Options;
import org.xbmc.nanisore.utils.values.Do;
import org.xbmc.nanisore.utils.values.Lazy;

import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicReference;

public class RemotePresenter implements Remote.Actions {

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

    public RemotePresenter(
            Remote.UseCases will,
            Remote.Rpc kodi,
            Options options
    ) {
        this.will = will;
        this.kodi = kodi;
        this.options = options;
    }

    @Override
    public void bind(final Remote.Ui view) {
        if (this.view != null) {
            throw new RuntimeException("Unbind first before rebinding!");
        }
        will.restore(new Do.Just<Remote.State>() {
            @Override
            public void got(Remote.State state) {
                if (!state.isHostPresent) {
                    view.goToHostAdder();
                    return;
                }
                RemotePresenter.this.view = view;
                RemotePresenter.this.state = state;
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
            will.save(state);
        }
    }

    @Override
    public void didShareVideo(final String urlString) {
        final AtomicReference<String> pluginUrl = new AtomicReference<>();
        Do.Seq.start(new Do.Executable<Boolean>() {
            @Override
            public void execute(final Do.Just<Boolean> next) {
                try {
                    String transformed = will.transformUrlToKodiPluginUrl(urlString);
                    if (transformed != null) {
                        pluginUrl.set(transformed);
                        will.maybeClearPlaylist(report(next));
                    } else {
                        Log.I.to(view, "cannot recognize share: %s", urlString);
                        report(Remote.Message.CANNOT_SHARE_VIDEO);
                    }
                } catch (MalformedURLException e) {
                    Log.E.to(view, "failed to parse video url: %s", urlString);
                    report(Remote.Message.CANNOT_SHARE_VIDEO);
                }
            }
        }).andThen(new Do.Step<Boolean, Void>() {
            @Override
            public void then(Boolean result, Do.Just<Void> next) {
                will.enqueueFile(pluginUrl.get(), result, report(next));
            }
        }).execute(new Do.Just<Void>() {
            @Override
            public void got(Void result) {
                state.videoToShare = null;
                if (view != null) {
                    view.refreshPlaylist();
                }
            }
        });
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

    @Override
    public void playerDidPlayOrPause(ListType.ItemsAll item) {
        if (view == null) {
            return;
        }
        String img = item.fanart;
        if (img == null || img.trim().isEmpty()) {
            img = item.thumbnail;
        }
        if (img != null && !img.trim().isEmpty() && !img.equals(state.imageUrl)) {
            img = kodi.tryGetImageUrl(img);
            view.setBackgroundImage(img);
            state.imageUrl = img;
        }
        if (!state.isObservingPlayer) {
            view.startObservingPlayerStatus();
            state.isObservingPlayer = true;
        }
    }

    @Override
    public void playerDidStop() {
        if (state.imageUrl != null && view != null) {
            view.setBackgroundImage(null);
            state.imageUrl = null;
        }
    }

    private void report(Remote.Message msg, Object... fmtArgs) {
        if (view != null) {
            view.tell(msg, fmtArgs);
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

    private <T> Do.Maybe<T> report(final Do.Just<T> handler) {
        return new Do.Maybe<T>() {
            @Override
            public void got(Do.Try<T> result) {
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

}
