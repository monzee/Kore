package org.xbmc.kore.screens.remote;

import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.type.PlayerType.GetActivePlayersReturnType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public interface Remote {

    interface Display {
        void tell(String message);
        void tell(Message message, Object... fmtArgs);
        void log(Log level, String message);
        void goToHostAdder();
        void initNavigationDrawer();
        void initTabs();
        void initActionBar();
        void toggleKeepAboveLockScreen(boolean enabled);
        void toggleKeepScreenOn(boolean enabled);
        boolean shouldInflateMenu();
        void promptTextToSend();
        void setToolbarTitle(String title);
        void setBackgroundImage(String url);
    }

    interface Actions {
        void bind(Display view);
        void unbind();
        void didShareVideo(String uriString);
        void didPressVolumeUp();
        void didPressVolumeDown();
        void didChoose(MenuAction action);
        void didSendText(String text, boolean done);
    }

    interface UseCases {

        /**
         * Called during unbind().
         *
         * The argument passed here should be returned on the next call to
         * restoreState().
         */
        void saveState(State state);

        /**
         * Called during bind().
         *
         * Should set the initial state on first run. Otherwise, return the
         * last state saved.
         */
        void restoreState(MightFail<? extends OnRestore> then);

        /**
         * Called before enqueuing a shared video URL.
         *
         * The playlist should not be cleared if there's a video being played
         * in the connected host.
         */
        void maybeClearPlaylist(MightFail<? extends OnMaybeClearPlaylist> then);

        /**
         * Called if the user intended to share a YouTube/Vimeo URL.
         *
         * The enqueued file should be played immediately if the playlist was
         * cleared.
         */
        void enqueueFile(String videoUri, boolean startPlaying, MightFail<?> then);

    }

    interface OnRestore {
        void restored(State state);
    }

    interface OnMaybeClearPlaylist {
        void playlistMaybeCleared(boolean decision);
    }

    abstract class MightFail<T> {
        public final T ok;

        public MightFail(T callback) {
            ok = callback;
        }

        public abstract void fail(Throwable error);
    }

    interface State {
    }

    /**
     * Synchronously wraps the RPC classes needed by this screen.
     *
     * Every method in this interface should block until the remote call has
     * returned, even the void methods. Any errors thrown should be an instance
     * of RpcError for proper feedback.
     */
    interface Rpc {
        List<GetActivePlayersReturnType> getActivePlayers();
        void clearPlaylist();
        void addToPlaylist(PlaylistType.Item item);
        void openPlaylist();
        void increaseVolume();
        void decreaseVolume();
        boolean isPvrEnabled();
    }

    interface Options {
        <T> T get(String key, T whenAbsent);
        <T> void put(String key, T value);
    }

    class RpcError extends RuntimeException {
        private static final long serialVersionUID = 1;
        public final Message type;
        public final int errorCode;
        public final String description;

        public RpcError(Message type, int errorCode, String description) {
            super("Kodi RPC error: " + description);
            this.type = type;
            this.errorCode = errorCode;
            this.description = description;
        }
    }

    enum Option { KEEP_ABOVE_LOCK_SCREEN, KEEP_SCREEN_ON, USE_HARDWARE_VOLUME_KEYS }

    enum MenuAction {
        WAKE_UP, QUIT, SUSPEND, REBOOT, SHUTDOWN, SEND_TEXT, FULLSCREEN,
        CLEAN_VIDEO_LIBRARY, CLEAN_AUDIO_LIBRARY, UPDATE_VIDEO_LIBRARY, UPDATE_AUDIO_LIBRARY
    }

    enum Message {
        GENERAL_ERROR,
        CANNOT_SHARE_VIDEO, CANNOT_GET_ACTIVE_PLAYER, CANNOT_ENQUEUE_FILE, CANNOT_PLAY_FILE
    }

    enum Log {
        D, I, E;

        public void to(Display console, String message) {
            if (console != null) {
                console.log(this, message);
            }
        }

        public void to(Display console, String tpl, Object... xs) {
            if (console != null) {
                console.log(this, String.format(tpl, xs));
            }
        }
    }

}
