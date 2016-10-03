package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Console;

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
        void showPressed(Button button);
        void showNormal(Button button);
        void say(String message, Object... fmtArgs);
        void mumble(String message, Object... fmtArgs);
        void show(
                String title,
                String details,
                String thumbnail,
                boolean showSkipIcons
        );
    }

    interface Actions {
        void bind(Ui view);
        void unbind();
        void didPress(Button button);
        void didLongPress(Button button);
        void didStartHoldingDown(Button button);
        void didStopHoldingDown(Button button);
    }

    interface UseCases extends Conventions<State> {
        void connectToEventServer(Maybe<?> then);
        void changeSpeed(boolean faster, Just<Integer> then);
        void fireAndLogTo(Console console, Runnable action);
        void fireAndFireAndFire(String name, Runnable action);
        void stop(String name);
        void stop();
    }

    class State {
        int activePlayerId;
        String nowPlayingItemType;
    }

    /**
     * Everything is sync because the default listener in the reference impl
     * logs all errors. Unfortunate because I can't optimize them by handling
     * logging in a single listener because the proxy shouldn't know about
     * the view.
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
        void tryOsd();
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
        LEFT, RIGHT, UP, DOWN,
        SELECT, BACK, INFO, CONTEXT, OSD,
        HOME, MOVIES, SHOWS, MUSIC, PICTURES,
        FORWARD, REWIND, PLAY, STOP
    }

}

