package org.xbmc.nanisore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.MightFail;

import java.util.List;

public interface Remote {

    interface Ui extends Console {
        void tell(Message message, Object... fmtArgs);
        void goToHostAdder();
        void initNavigationDrawer();
        void initTabs(int activeTab);
        void initActionBar();
        void toggleKeepAboveLockScreen(boolean enabled);
        void toggleKeepScreenOn(boolean enabled);
        boolean shouldInflateMenu();
        void promptTextToSend();
        void setToolbarTitle(String title);
        void setBackgroundImage(String url);
    }

    interface Actions {
        void bind(Ui view);
        void unbind();
        void didShareVideo(String uriString);
        void didPressVolumeUp();
        void didPressVolumeDown();
        void didChoose(MenuAction action);
        void didSendText(String text, boolean done);
        void didSwitchTab(int position);
    }

    interface UseCases {

        /**
         * Called during unbind().
         *
         * @param state This should be returned on the next call to
         *              restoreState().
         */
        void saveState(State state);

        /**
         * Called during bind().
         *
         * Should set the initial state on first run. Otherwise, return the
         * last state saved.
         *
         * @param then Will receive the saved or initial state. This can't
         *             possibly fail.
         */
        void restoreState(OnRestore then);

        /**
         * Called before enqueuing a shared video URL.
         *
         * The playlist should not be cleared if there's a video being played
         * in the connected host.
         *
         * @param then Will receive a bool indicating whether the playlist was
         *             cleared or not.
         */
        void maybeClearPlaylist(MightFail<? extends OnMaybeClearPlaylist> then);

        /**
         * Called if the user intended to share a YouTube/Vimeo URL.
         *
         * The enqueued file should be played immediately if the playlist was
         * cleared.
         *
         * @param videoUri Should be a valid Kodi plugin url.
         * @param then Does not do anything on success; will call the error
         *             handler otherwise.
         */
        void enqueueFile(String videoUri, boolean startPlaying, MightFail<?> then);

        /**
         * Run action in the background and return immediately.
         *
         * Don't care if it succeeds or fails. It will probably succeed.
         */
        void fireAndForget(Runnable action);

    }

    interface OnRestore {
        void restored(State state);
    }

    interface OnMaybeClearPlaylist {
        void playlistMaybeCleared(boolean wasCleared);
    }

    class State {
        int activeTab;
        boolean sharedVideoEnqueued;
    }

    /**
     * Synchronously wraps the RPC classes needed by this screen.
     *
     * Every method in this interface should block until the remote call has
     * returned, even the void methods. Any errors thrown should be an instance
     * of RpcError for proper feedback.
     */
    interface Rpc {
        void dispose();
        List<PlayerType.GetActivePlayersReturnType> getActivePlayers();
        void clearVideoPlaylist();
        void addToVideoPlaylist(PlaylistType.Item item);
        void openVideoPlaylist();
        void increaseVolume();
        void decreaseVolume();
    }

    class RpcError extends RuntimeException {
        private static final long serialVersionUID = 1;
        public final Message type;
        public final int errorCode;
        public final String description;

        RpcError(Message type, int errorCode, String description) {
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

}
