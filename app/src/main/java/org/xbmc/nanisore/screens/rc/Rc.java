package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.MightFail;

/**
 * This is a refactoring of {@link org.xbmc.kore.ui.RemoteFragment}.
 *
 * RC as in remote control. "Remote" is already taken; "RemoteControl" is
 * too long.
 */
public interface Rc {

    interface Ui extends Console {
        void togglePlayPauseIcon(boolean showPlay);
        void toggleMediaInfoPanel(boolean showMedia);
        void toggleRemotePanel(boolean visible);
        void say(String message, Object... fmtArgs);
        void mumble(String message, Object... fmtArgs);
        void show(
                String title,
                String subtitle,
                String thumbnail,
                boolean showSkipIcons
        );
    }

    interface Actions {
        void bind(Ui view);
        void unbind();
        void didPress(Button button);
    }

    interface UseCases extends Conventions<State> {
        void connectToEventServer(MightFail<?> then);
        void changeSpeed(boolean faster, OnSpeedChange then);
        void fireAndLog(Runnable action);
    }

    class State {
        int activePlayerId;
        String nowPlayingItemType;
    }

    interface OnSpeedChange {
        void speedChanged(int result);
    }

    /**
     * Everything is sync because the default listener in the current impl
     * logs all errors.
     */
    interface Rpc {
        void tryLeft(boolean viaPacket);
        void tryRight(boolean viaPacket);
        void tryUp(boolean viaPacket);
        void tryDown(boolean viaPacket);
        void trySelect();
        void tryBack();
        void tryInfo();
        void tryCodecInfo();
        void tryContextMenu();
        void tryHome();
        void tryMovies();
        void tryShows();
        void tryMusic();
        void tryPictures();
        void trySpeedUp();
        void trySlowDown();
        void tryFastForward();
        void tryRewind();
        void tryPlay();
        void tryStop();
    }

    enum Button {
        LEFT, RIGHT, UP, DOWN, SELECT, BACK, INFO, CONTEXT,
        HOME, MOVIES, SHOWS, MUSIC, PICTURES,
        FORWARD, REWIND, PLAY, STOP
    }

    enum Message {
        CANT_SETUP_EVENT_SERVER
    }

}

