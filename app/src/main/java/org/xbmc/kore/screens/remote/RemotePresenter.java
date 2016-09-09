package org.xbmc.kore.screens.remote;

import org.xbmc.kore.jsonrpc.type.PlayerType;

import java.util.List;

public class RemotePresenter implements Remote.Actions, Remote.UseCases {

    private Remote.Display view;

    @Override
    public void bind(Remote.Display view) {
        this.view = view;
        view.initNavigationDrawer();
        view.initTabs();
        view.initActionBar();
    }

    @Override
    public void unbind() {
        view = null;
    }

    @Override
    public void didSendVideoUri(String uriString) {

    }

    @Override
    public void didPressVolumeUp() {
    }

    @Override
    public void didPressVolumeDown() {
    }

    @Override
    public void didChoose(Remote.MenuAction action) {
    }

    @Override
    public void didSendText(String text, boolean done) {
    }

    @Override
    public void checkHostPresence() {

    }

    @Override
    public void hostPresenceChecked(boolean found) {

    }

    @Override
    public void fetchActivePlayers() {

    }

    @Override
    public void activePlayersFetched(List<PlayerType.GetActivePlayersReturnType> players) {

    }

    @Override
    public void clearPlaylist() {

    }

    @Override
    public void playlistCleared() {

    }

    @Override
    public void checkFlag(Remote.Option opt) {

    }

    @Override
    public void flagChecked(Remote.Option opt, boolean value) {

    }

    @Override
    public void checkPvrEnabledForCurrentHost() {

    }

    @Override
    public void pvrEnabledChecked(String key, boolean status) {

    }

    @Override
    public String tryParseVimeoUrl(String path) {
        return null;
    }

    @Override
    public String tryPraseYoutubeUrl(String query) {
        return null;
    }

    @Override
    public void enqueueFile(String videoUri, boolean startPlaylist) {

    }

    @Override
    public void increaseVolume() {

    }

    @Override
    public void decreaseVolume() {

    }
}
