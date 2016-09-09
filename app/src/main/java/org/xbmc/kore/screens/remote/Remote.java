package org.xbmc.kore.screens.remote;

import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.type.PlayerType;

import java.util.List;

public interface Remote {

    interface Display {
        void tell(String message);
        void log(Log level, String message);
        void goAddHost(int... flags);
        void initNavigationDrawer();
        void initTabs();
        void initActionBar();
        void toggleKeepAboveLockScreen(boolean enabled);
        void toggleKeepScreenOn(boolean enabled);
        boolean shouldInflateMenu();
        void promptTextToSend();
        void setToolbarTitle(String title);
        void setBackgroundImage(HostManager hostManager, String url);
    }

    interface Actions {
        void bind(Display view);
        void unbind();
        void didSendVideoUri(String uriString);
        void didPressVolumeUp();
        void didPressVolumeDown();
        void didChoose(MenuAction action);
        void didSendText(String text, boolean done);
    }

    interface UseCases {
        void checkHostPresence();
        void hostPresenceChecked(boolean found);

        void fetchActivePlayers();
        void activePlayersFetched(List<PlayerType.GetActivePlayersReturnType> players);

        void clearPlaylist();
        void playlistCleared();

        void checkFlag(Option opt);
        void flagChecked(Option opt, boolean value);

        void checkPvrEnabledForCurrentHost();
        void pvrEnabledChecked(String key, boolean status);

        String tryParseVimeoUrl(String path);
        String tryPraseYoutubeUrl(String query);
        void enqueueFile(String videoUri, boolean startPlaylist);
        void increaseVolume();
        void decreaseVolume();
    }

    interface Options {
        boolean getBoolean(String key);
        void putBoolean(String key, boolean value);
    }

    interface WhenBound {
        void with(Display view);
    }

    interface ViewBound {
        boolean run(WhenBound action);
    }

    enum Option { KEEP_ABOVE_LOCK_SCREEN, KEEP_SCREEN_ON, USE_HARDWARE_VOLUME_KEYS }

    enum MenuAction {
        WAKE_UP, QUIT, SUSPEND, REBOOT, SHUTDOWN, SEND_TEXT, FULLSCREEN,
        CLEAN_VIDEO_LIBRARY, CLEAN_AUDIO_LIBRARY, UPDATE_VIDEO_LIBRARY, UPDATE_AUDIO_LIBRARY
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
