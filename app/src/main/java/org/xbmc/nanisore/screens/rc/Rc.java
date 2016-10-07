package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Console;
import org.xbmc.nanisore.utils.values.Do;

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
        void connectToEventServer(Do.Maybe<Void> then);
        void changeSpeed(boolean faster, Do.Just<Integer> then);
        void fireAndLogTo(Console console, Runnable action);
        void fireRepeatedly(String name, Runnable action);
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
        void trySpeedUp(int playerId);
        void trySlowDown(int playerId);
        void tryFastForward(int playerId);
        void tryRewind(int playerId);
        void tryPlay(int playerId);
        void tryStop(int playerId);
    }

    enum Button {
        LEFT, RIGHT, UP, DOWN,
        SELECT, BACK, INFO, CONTEXT, OSD,
        HOME, MOVIES, SHOWS, MUSIC, PICTURES,
        FORWARD, REWIND, PLAY, STOP
    }

    class RpcError extends RuntimeException {
        public final int code;
        public final String description;

        RpcError(int code, String description) {
            super("Kodi RPC error: " + description);
            this.code = code;
            this.description = description;
        }
    }

}

