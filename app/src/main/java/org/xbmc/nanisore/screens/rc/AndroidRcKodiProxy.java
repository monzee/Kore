package org.xbmc.nanisore.screens.rc;

import android.os.Handler;
import android.os.Looper;

import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.GUI;
import org.xbmc.kore.jsonrpc.method.Input;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.type.GlobalType;
import org.xbmc.nanisore.utils.values.Either;


public class AndroidRcKodiProxy implements Rc.Rpc {

    private final HostConnection host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidRcKodiProxy(HostConnection host) {
        this.host = host;
    }

    @Override
    public void tryLeft(boolean viaPacket) {
        execSync(new Input.Left());
    }

    @Override
    public void tryRight(boolean viaPacket) {
        execSync(new Input.Right());
    }

    @Override
    public void tryUp(boolean viaPacket) {
        execSync(new Input.Up());
    }

    @Override
    public void tryDown(boolean viaPacket) {
        execSync(new Input.Down());
    }

    @Override
    public void trySelect() {
        execSync(new Input.Select());
    }

    @Override
    public void tryBack() {
        execSync(new Input.Back());
    }

    @Override
    public void tryInfo() {
        execSync(new Input.ExecuteAction(Input.ExecuteAction.INFO));
    }

    @Override
    public void tryCodecInfo() {
        execSync(new Input.ExecuteAction(Input.ExecuteAction.CODECINFO));
    }

    @Override
    public void tryContextMenu() {
        execSync(new Input.ExecuteAction(Input.ExecuteAction.CONTEXTMENU));
    }

    @Override
    public void tryOsd() {
        execSync(new Input.ExecuteAction(Input.ExecuteAction.OSD));
    }

    @Override
    public void tryHome() {
        execSync(new GUI.ActivateWindow(GUI.ActivateWindow.HOME));
    }

    @Override
    public void tryMovies() {
        execSync(new GUI.ActivateWindow(
                GUI.ActivateWindow.VIDEOS,
                GUI.ActivateWindow.PARAM_MOVIE_TITLES
        ));
    }

    @Override
    public void tryShows() {
        execSync(new GUI.ActivateWindow(
                GUI.ActivateWindow.VIDEOS,
                GUI.ActivateWindow.PARAM_TV_SHOWS_TITLES
        ));
    }

    @Override
    public void tryMusic() {
        execSync(new GUI.ActivateWindow(GUI.ActivateWindow.MUSICLIBRARY));
    }

    @Override
    public void tryPictures() {
        execSync(new GUI.ActivateWindow(GUI.ActivateWindow.PICTURES));
    }

    @Override
    public void trySpeedUp(int playerId) {
        execSync(new Player.SetSpeed(playerId, GlobalType.IncrementDecrement.INCREMENT));
    }

    @Override
    public void trySlowDown(int playerId) {
        execSync(new Player.SetSpeed(playerId, GlobalType.IncrementDecrement.DECREMENT));
    }

    @Override
    public void tryFastForward(int playerId) {
        execSync(new Player.GoTo(playerId, Player.GoTo.NEXT));
    }

    @Override
    public void tryRewind(int playerId) {
        execSync(new Player.GoTo(playerId, Player.GoTo.PREVIOUS));
    }

    @Override
    public void tryPlay(int playerId) {
        execSync(new Player.PlayPause(playerId));
    }

    @Override
    public void tryStop(int playerId) {
        execSync(new Player.Stop(playerId));
    }

    private <T> T execSync(ApiMethod<T> method) {
        final Either<Throwable, T> either = new Either<>();
        host.execute(method, new ApiCallback<T>() {
            @Override
            public void onSuccess(T result) {
                either.right(result);
            }

            @Override
            public void onError(int errorCode, String description) {
                either.left(new Rc.RpcError(errorCode, description));
            }
        }, handler);
        try {
            return either.get();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
