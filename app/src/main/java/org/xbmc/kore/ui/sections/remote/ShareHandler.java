package org.xbmc.kore.ui.sections.remote;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import org.xbmc.kore.R;
import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.ApiException;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.method.Playlist;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.utils.Task;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.greenrobot.event.EventBus;

/**
 * Stand-alone class that sends shared URLs to Kodi plugins.
 */
public class ShareHandler {

    public static final String TAG = ShareHandler.class.getCanonicalName();
    public static final String KEY_HANDLED = TAG + ":share-handled";

    public interface Strings {
        /**
         * @param id of string resource
         * @param fmtArgs arguments to sprintf if any
         * @return a translated string
         */
        String get(@StringRes int id, Object... fmtArgs);

        /**
         * @param message to show in a toast
         */
        void toast(String message);
    }

    private static final String YOUTUBE_PREFIX = "plugin://plugin.video.youtube/play/?video_id=";
    private static final String YOUTUBE_SHORT_URL = "(?i)://youtu\\.be/([^\\?\\s/]+)";
    private static final String YOUTUBE_LONG_URL = "(?i)://(?:www\\.|m\\.)?youtube\\.com/watch\\S*[\\?&]v=([^&\\s]+)";
    private static final String TWITCH_PREFIX = "plugin://plugin.video.twitch/playLive/%s/";
    private static final String TWITCH_URL = "(?i)://(?:www\\.)?twitch\\.tv/([^\\?\\s/]+)";
    private static final String VIMEO_PREFIX = "plugin://plugin.video.vimeo/play/?video_id=";
    private static final String VIMEO_URL = "(?i)://(?:www\\.|player\\.)?vimeo\\.com[^\\?\\s]*?/(\\d+)";
    private static final String SVTPLAY_PREFIX = "plugin://plugin.video.svtplay/?url=%s&mode=video";
    private static final String SVTPLAY_URL = "(?i)://(?:www\\.)?svtplay\\.se(/video/\\d+/.*)";

    /**
     * Tries to match a bunch of URL patterns and converts the first match into
     * a kodi plugin url.
     *
     * @param data From the EXTRA_TEXT param or the stringified intent data uri
     * @return null when no url is recognized.
     */
    static String urlFrom(@Nullable String data) {
        if (data == null) {
            return null;
        }
        Matcher m = Pattern.compile(YOUTUBE_LONG_URL).matcher(data);
        if (m.find()) {
            return YOUTUBE_PREFIX + m.group(1);
        }
        // possibly captured through EXTRA_TEXT param
        m = Pattern.compile(YOUTUBE_SHORT_URL).matcher(data);
        if (m.find()) {
            return YOUTUBE_PREFIX + m.group(1);
        }
        m = Pattern.compile(VIMEO_URL).matcher(data);
        if (m.find()) {
            return VIMEO_PREFIX + m.group(1);
        }
        // captured through EXTRA_TEXT param
        m = Pattern.compile(TWITCH_URL).matcher(data);
        if (m.find()) {
            return String.format(TWITCH_PREFIX, m.group(1));
        }
        m = Pattern.compile(SVTPLAY_URL).matcher(data);
        if (m.find()) {
            return String.format(SVTPLAY_PREFIX, Uri.encode(m.group(1)));
        }
        return null;
    }

    private final HostConnection connection;
    private final Strings strings;
    private final EventBus bus;

    /**
     * @param connection Connection to Kodi
     * @param bus An instance of EventBus; probably EventBus.getDefault()
     * @param strings Hook into the calling activity for translated strings
     *                and toasts
     */
    public ShareHandler(HostConnection connection, EventBus bus, Strings strings) {
        this.connection = connection;
        this.strings = strings;
        this.bus = bus;
    }

    /**
     * Tries to find known URLs in the intent and sends it to Kodi.
     *
     * @param intent that started the activity
     * @return true when it was able to try matching the known url patterns
     * regardless of success; false when there is no connection or action so
     * you might want to try again.
     */
    public boolean handle(Intent intent) {
        String action = intent.getAction();
        if (connection == null || action == null) {
            return false;
        }

        String pluginUrl;
        switch (action) {
            case Intent.ACTION_SEND:
                pluginUrl = urlFrom(intent.getStringExtra(Intent.EXTRA_TEXT));
                break;
            case Intent.ACTION_VIEW:
                pluginUrl = urlFrom(intent.getDataString());
                break;
            default:
                return true;
        }

        if (pluginUrl == null) {
            say(R.string.error_share_video);
            return true;
        }

        final Handler handler = new Handler();
        final String file = pluginUrl;
        new Task.Sequence<>(isPlayingVideo(handler), new Task.Bind<Boolean, String>() {
            @Override
            public Task<String> from(Boolean isPlaying) {
                if (isPlaying) {
                    return Task.Sequence.of(enqueue(handler, file))
                            .then(hostNotify(handler, strings.get(R.string.shared_video_added)));
                } else {
                    return Task.Sequence.of(clearPlaylist(handler))
                            .then(enqueue(handler, file))
                            .then(play(handler));
                }
            }
        }).start(refreshPlaylist());
        return true;
    }

    private Task<Boolean> isPlayingVideo(final Handler handler) {
        return new Task<Boolean>() {
            @Override
            public void start(@NonNull final OnFinish<? super Boolean> then) {
                connection.execute(
                        new Player.GetActivePlayers(),
                        callOrSay(new OnFinish<ArrayList<GetActivePlayersReturnType>>() {
                            @Override
                            public void got(ArrayList<GetActivePlayersReturnType> result) {
                                for (GetActivePlayersReturnType player : result) {
                                    if (player.type.equals(GetActivePlayersReturnType.VIDEO)) {
                                        then.got(true);
                                        return;
                                    }
                                }
                                then.got(false);
                            }
                        }, R.string.error_get_active_player),
                        handler);
            }
        };
    }

    private Task<String> clearPlaylist(final Handler handler) {
        return new Task<String>() {
            @Override
            public void start(@NonNull OnFinish<? super String> then) {
                connection.execute(
                        new Playlist.Clear(PlaylistType.VIDEO_PLAYLISTID),
                        callOrSay(then, R.string.error_queue_media_file),
                        handler);
            }
        };
    }

    private Task<String> enqueue(final Handler handler, String file) {
        final PlaylistType.Item item = new PlaylistType.Item();
        item.file = file;
        return new Task<String>() {
            @Override
            public void start(@NonNull OnFinish<? super String> then) {
                connection.execute(
                        new Playlist.Add(PlaylistType.VIDEO_PLAYLISTID, item),
                        callOrSay(then, R.string.error_queue_media_file),
                        handler);
            }
        };
    }

    private Task<String> hostNotify(final Handler handler, final String message) {
        return new Task<String>() {
            @Override
            public void start(@NonNull final OnFinish<? super String> then) {
                connection.execute(
                        new Player.Notification(strings.get(R.string.app_name), message),
                        new ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                // this will never be hit, but call it anyway. it might be fixed in
                                // the future, who knows.
                                then.got(result);
                            }

                            @Override
                            public void onError(int errorCode, String description) {
                                // okhttp will barf here because of the 0-length response.
                                // there's literally no response, the server just drops the socket.
                                // is there a way to tell okhttp that this is expected?
                                if (errorCode == ApiException.IO_EXCEPTION_WHILE_SENDING_REQUEST) {
                                    then.got("");
                                } else {
                                    say(R.string.error_message, description);
                                }
                            }
                        },
                        handler);
            }
        };
    }

    private Task<String> play(final Handler handler) {
        return new Task<String>() {
            @Override
            public void start(@NonNull OnFinish<? super String> then) {
                connection.execute(
                        new Player.Open(Player.Open.TYPE_PLAYLIST, PlaylistType.VIDEO_PLAYLISTID),
                        callOrSay(then, R.string.error_play_media_file),
                        handler);
            }
        };
    }

    private <T> ApiCallback<T>
    callOrSay(final Task.OnFinish<? super T> then, @StringRes final int error) {
        return new ApiCallback<T>() {
            @Override
            public void onSuccess(T result) {
                then.got(result);
            }

            @Override
            public void onError(int errorCode, String description) {
                say(error, description);
            }
        };
    }

    private Task.OnFinish<Object> refreshPlaylist() {
        return new Task.OnFinish<Object>() {
            @Override
            public void got(Object unused) {
                bus.post(Event.REFRESH_PLAYLIST);
            }
        };
    }

    private void say(@StringRes int message, Object... fmtArgs) {
        strings.toast(strings.get(message, fmtArgs));
    }

}
