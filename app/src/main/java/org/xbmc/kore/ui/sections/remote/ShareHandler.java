package org.xbmc.kore.ui.sections.remote;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import org.xbmc.kore.R;
import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.method.Playlist;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.utils.Either;
import org.xbmc.kore.utils.Result;
import org.xbmc.kore.utils.Transform;

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
         * @param message to show the user
         */
        void say(String message);
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

    private static class Error {
        final @StringRes int message;
        final String description;

        Error(@StringRes int message, String description) {
            this.message = message;
            this.description = description;
        }
    }

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
     * @return true when an attempt was made to find recognized urls; false when
     * there is no connection so you might want to try again later. True doesn't
     * mean it found a match or sent the URL to Kodi successfully.
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
            strings.say(strings.get(R.string.error_share_video));
            return true;
        }

        final Handler handler = new Handler();
        final String file = pluginUrl;
        Result.of(isPlayingVideo(handler)).then(new Either.Bind<Error, Boolean, String>() {
            @Override
            public Either<Error, String> from(Boolean isPlaying) {
                if (isPlaying) {
                    return Either.Monad.of(enqueue(handler, file))
                            .then(hostNotify(handler, strings.get(R.string.item_added_to_playlist)));
                } else {
                    return Either.Monad.of(clearPlaylist(handler))
                            .then(enqueue(handler, file))
                            .then(play(handler));
                }
            }
        }).recover(new Transform<Either.Monad<Error, String>, Either<Error, String>>() {
            @Override
            public Either<Error, String> from(Either.Monad<Error, String> result) {
                return okHttpHatesNotifications(result);
            }
        }).match(new Either.Pattern<Error, String>() {
            @Override
            public void ok(String ignored) {
                bus.post(Event.REFRESH_PLAYLIST);
            }

            @Override
            public void fail(Error e) {
                strings.say(strings.get(e.message, e.description));
            }
        });
        return true;
    }

    private Either<Error, Boolean> isPlayingVideo(final Handler handler) {
        return new Either<Error, Boolean>() {
            @Override
            public void match(final Pattern<? super Error, ? super Boolean> matcher) {
                connection.execute(
                        new Player.GetActivePlayers(),
                        passOrSay(new Pattern<Error, ArrayList<GetActivePlayersReturnType>>() {
                            @Override
                            public void ok(ArrayList<GetActivePlayersReturnType> players) {
                                for (GetActivePlayersReturnType player : players) {
                                    if (player.type.equals(GetActivePlayersReturnType.VIDEO)) {
                                        matcher.ok(true);
                                        return;
                                    }
                                }
                                matcher.ok(false);
                            }

                            @Override
                            public void fail(Error error) {
                                matcher.fail(error);
                            }
                        }, R.string.error_get_active_player),
                        handler);
            }
        };
    }

    private Either<Error, String> clearPlaylist(final Handler handler) {
        return new Either<Error, String>() {
            @Override
            public void match(final Pattern<? super Error, ? super String> matcher) {
                connection.execute(
                        new Playlist.Clear(PlaylistType.VIDEO_PLAYLISTID),
                        passOrSay(matcher, R.string.error_queue_media_file),
                        handler);
            }
        };
    }

    private Either<Error, String> enqueue(final Handler handler, String file) {
        final PlaylistType.Item item = new PlaylistType.Item();
        item.file = file;
        return new Either<Error, String>() {
            @Override
            public void match(final Pattern<? super Error, ? super String> matcher) {
                connection.execute(
                        new Playlist.Add(PlaylistType.VIDEO_PLAYLISTID, item),
                        passOrSay(matcher, R.string.error_queue_media_file),
                        handler);
            }
        };
    }

    private Either<Error, String> hostNotify(final Handler handler, final String message) {
        return new Either<Error, String>() {
            @Override
            public void match(final Pattern<? super Error, ? super String> matcher) {
                connection.execute(
                        new Player.Notification(strings.get(R.string.app_name), message),
                        passOrSay(matcher, R.string.error_message),
                        handler);
            }
        };
    }

    private Either<Error, String> play(final Handler handler) {
        return new Either<Error, String>() {
            @Override
            public void match(final Pattern<? super Error, ? super String> matcher) {
                connection.execute(
                        new Player.Open(Player.Open.TYPE_PLAYLIST, PlaylistType.VIDEO_PLAYLISTID),
                        passOrSay(matcher, R.string.error_play_media_file),
                        handler);
            }
        };
    }

    private static <T> ApiCallback<T>
    passOrSay(final Either.Pattern<? super Error, ? super T> matcher, final @StringRes int message) {
        return new ApiCallback<T>() {
            @Override
            public void onSuccess(T result) {
                matcher.ok(result);
            }

            @Override
            public void onError(int errorCode, String description) {
                matcher.fail(new Error(message, description));
            }
        };
    }

    /*
     * It seems Kodi unceremoniously drops the socket after receiving a
     * notification-type request. OkHttp doesn't like that. This brings the
     * Either back to the successful side if it contains this specific error.
     */
    private static <T> Either<Error, String>
    okHttpHatesNotifications(final Either<Error, T> either) {
        return new Either<Error, String>() {
            @Override
            public void match(final Pattern<? super Error, ? super String> matcher) {
                either.match(new Pattern<Error, T>() {
                    @Override
                    public void ok(T value) {
                        matcher.ok(String.valueOf(value));
                    }

                    @Override
                    public void fail(Error error) {
                        if (error.description.contains("unexpected end of stream")) {
                            matcher.ok("");
                        } else {
                            matcher.fail(error);
                        }
                    }
                });
            }
        };
    }

}
