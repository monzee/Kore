package org.xbmc.nanisore.screens.remote;

import android.os.Handler;
import android.os.Looper;

import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Application;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.method.Playlist;
import org.xbmc.kore.jsonrpc.type.GlobalType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.nanisore.utils.Either;

import java.util.List;

public class AndroidRemoteHostProxy implements Remote.Rpc {

    private final HostConnection host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidRemoteHostProxy(HostConnection host) {
        this.host = host;
    }

    @Override
    public void dispose() {
        host.disconnect();
    }

    @Override
    public List<PlayerType.GetActivePlayersReturnType> getActivePlayers() {
        return execSync(new Player.GetActivePlayers(), Remote.Message.CANNOT_GET_ACTIVE_PLAYER);
    }

    @Override
    public void clearVideoPlaylist() {
        execSync(
                new Playlist.Clear(PlaylistType.VIDEO_PLAYLISTID),
                Remote.Message.CANNOT_ENQUEUE_FILE
        );
    }

    @Override
    public void addToVideoPlaylist(PlaylistType.Item item) {
        execSync(
                new Playlist.Add(PlaylistType.VIDEO_PLAYLISTID, item),
                Remote.Message.CANNOT_ENQUEUE_FILE
        );
    }

    @Override
    public void openVideoPlaylist() {
        execSync(
                new Player.Open(Player.Open.TYPE_PLAYLIST, PlaylistType.VIDEO_PLAYLISTID),
                Remote.Message.CANNOT_PLAY_FILE
        );
    }

    @Override
    public void increaseVolume() {
        execSync(
                new Application.SetVolume(GlobalType.IncrementDecrement.INCREMENT),
                Remote.Message.GENERAL_ERROR
        );
    }

    @Override
    public void decreaseVolume() {
        execSync(
                new Application.SetVolume(GlobalType.IncrementDecrement.DECREMENT),
                Remote.Message.GENERAL_ERROR
        );
    }

    private <T> T execSync(ApiMethod<T> method, final Remote.Message errorMessage) {
        final Either<Remote.RpcError, T> value = new Either<>();
        host.execute(method, new ApiCallback<T>() {
            @Override
            public void onSuccess(T result) {
                value.right(result);
            }

            @Override
            public void onError(int errorCode, String description) {
                value.left(new Remote.RpcError(
                        errorMessage,
                        errorCode,
                        description
                ));
            }
        }, handler);
        try {
            return value.get();
        } catch (InterruptedException e) {
            throw new Remote.RpcError(Remote.Message.GENERAL_ERROR, 0, e.getLocalizedMessage());
        }
    }

}
