package em.zed.connection;

/*
 * This file is a part of the Kore project.
 */

import org.xbmc.kore.jsonrpc.notification.Application;
import org.xbmc.kore.jsonrpc.notification.Input;
import org.xbmc.kore.jsonrpc.notification.Player;
import org.xbmc.kore.jsonrpc.notification.System;

import em.zed.backend.ApiClient;

class ConsolidateNotifications
        implements ApiClient.OnPlayerNotification,
        ApiClient.OnSystemNotification,
        ApiClient.OnInputNotification,
        ApiClient.OnApplicationNotification {
    private final Connection.HostEvents on;

    ConsolidateNotifications(Connection.HostEvents on) {
        this.on = on;
    }

    @Override
    public void playerPropertyChanged(Player.OnPropertyChanged notification) {
        update(notification.data);
    }

    @Override
    public void playerPlayed(Player.OnPlay notification) {
        update(notification.data);
    }

    @Override
    public void playerPaused(Player.OnPause notification) {
        update(notification.data);
    }

    @Override
    public void playerSpeedChanged(Player.OnSpeedChanged notification) {
        update(notification.data);
    }

    @Override
    public void playerSought(Player.OnSeek notification) {
        on.playerPositionChanged(
                notification.seekoffset,
                notification.time,
                notification.item
        );
    }

    @Override
    public void playerStopped(Player.OnStop notification) {
        on.playerStopped(notification.end, notification.item);
    }

    @Override
    public void systemQuit(System.OnQuit notification) {
        on.aboutToExit();
    }

    @Override
    public void systemRestarted(System.OnRestart notification) {
        on.aboutToExit();
    }

    @Override
    public void systemSlept(System.OnSleep notification) {
        on.aboutToExit();
    }

    @Override
    public void inputRequested(Input.OnInputRequested notification) {
        on.inputRequested(notification.title, notification.type, notification.value);
    }

    @Override
    public void appVolumeChanged(Application.OnVolumeChanged notification) {
        on.appVolumeChanged(notification.volume, notification.muted);
    }

    private void update(Player.NotificationsData data) {
        Boolean shuffled = data.property.shuffled;
        on.playerStateChanged(
                data.player.playerId,
                data.player.speed,
                shuffled == null ? false : shuffled,
                data.property.repeatMode,
                data.item
        );
    }

}
