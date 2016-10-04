package org.xbmc.nanisore.screens.remote;

import android.os.Handler;
import android.os.Looper;

import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.jsonrpc.ApiCallback;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Application;
import org.xbmc.kore.jsonrpc.method.AudioLibrary;
import org.xbmc.kore.jsonrpc.method.GUI;
import org.xbmc.kore.jsonrpc.method.Input;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.method.Playlist;
import org.xbmc.kore.jsonrpc.method.System;
import org.xbmc.kore.jsonrpc.method.VideoLibrary;
import org.xbmc.kore.jsonrpc.type.GlobalType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.utils.NetUtils;
import org.xbmc.nanisore.utils.values.Either;

import java.util.List;

/**
 * The synchronous methods (named `try*`) WILL DEADLOCK THE MAIN THREAD if
 * called directly! Always use a non-blocking
 * {@link org.xbmc.nanisore.utils.scheduling.Runner}! The other methods
 * should be fine either way, but prefer calling them directly to minimize
 * thread nesting.
 *
 * TODO: if i use a dedicated HandlerThread, it won't be a problem
 * but i'd need to clean it up later and check for validity for every call
 */
public class AndroidRemoteKodiProxy implements Remote.Rpc {

    private final HostConnection host;
    private final HostInfo hostInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidRemoteKodiProxy(HostConnection host, HostInfo hostInfo) {
        this.host = host;
        this.hostInfo = hostInfo;
    }

    @Override
    public void dispose() {
        host.disconnect();
    }

    @Override
    public List<PlayerType.GetActivePlayersReturnType> tryGetActivePlayers() {
        return execSync(
                new Player.GetActivePlayers(),
                Remote.Message.CANNOT_GET_ACTIVE_PLAYER
        );
    }

    @Override
    public void tryClearVideoPlaylist() {
        execSync(
                new Playlist.Clear(PlaylistType.VIDEO_PLAYLISTID),
                Remote.Message.CANNOT_ENQUEUE_FILE
        );
    }

    @Override
    public void tryAddToVideoPlaylist(PlaylistType.Item item) {
        execSync(
                new Playlist.Add(PlaylistType.VIDEO_PLAYLISTID, item),
                Remote.Message.CANNOT_ENQUEUE_FILE
        );
    }

    @Override
    public void tryOpenVideoPlaylist() {
        execSync(
                new Player.Open(Player.Open.TYPE_PLAYLIST, PlaylistType.VIDEO_PLAYLISTID),
                Remote.Message.CANNOT_PLAY_FILE
        );
    }

    @Override
    public void tryWakeUp() {
        NetUtils.sendWolMagicPacket(
                hostInfo.getMacAddress(),
                hostInfo.getAddress(),
                hostInfo.getWolPort()
        );
    }

    @Override
    public String tryGetImageUrl(String image) {
        return hostInfo.getImageUrl(image);
    }

    @Override
    public void increaseVolume() {
        execAsync(new Application.SetVolume(GlobalType.IncrementDecrement.INCREMENT));
    }

    @Override
    public void decreaseVolume() {
        execAsync(new Application.SetVolume(GlobalType.IncrementDecrement.DECREMENT));
    }

    @Override
    public void sendText(String text, boolean done) {
        execAsync(new Input.SendText(text, done));
    }

    @Override
    public void quit() {
        execAsync(new Application.Quit());
    }

    @Override
    public void suspend() {
        execAsync(new System.Suspend());
    }

    @Override
    public void reboot() {
        execAsync(new System.Reboot());
    }

    @Override
    public void shutdown() {
        execAsync(new System.Shutdown());
    }

    @Override
    public void toggleFullScreen() {
        execAsync(new GUI.SetFullscreen());
    }

    @Override
    public void cleanVideoLibrary() {
        execAsync(new VideoLibrary.Clean());
    }

    @Override
    public void cleanAudioLibrary() {
        execAsync(new AudioLibrary.Clean());
    }

    @Override
    public void updateVideoLibrary() {
        execAsync(new VideoLibrary.Scan());
    }

    @Override
    public void updateAudioLibrary() {
        execAsync(new AudioLibrary.Scan());
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

    private void execAsync(ApiMethod<?> method) {
        host.execute(method, null, null);
    }

}
