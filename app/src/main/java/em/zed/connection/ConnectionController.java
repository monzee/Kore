package em.zed.connection;

/*
 * This file is a part of the Kore project.
 */

import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.ApiException;
import org.xbmc.kore.jsonrpc.HostConnection;
import org.xbmc.kore.jsonrpc.method.Application;
import org.xbmc.kore.jsonrpc.method.Player;
import org.xbmc.kore.jsonrpc.type.ApplicationType;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import em.zed.backend.ApiClient;
import em.zed.backend.HttpClient;
import em.zed.backend.TcpClient;
import em.zed.util.Links;
import em.zed.util.LogLevel;
import em.zed.util.State;

public class ConnectionController {

    private static final String[] APP_VOLUME = {
            Application.GetProperties.VOLUME,
            Application.GetProperties.MUTED,
    };

    private static final String[] PLAYER_PROPERTIES = {
            PlayerType.PropertyName.SPEED,
            PlayerType.PropertyName.PERCENTAGE,
            PlayerType.PropertyName.POSITION,
            PlayerType.PropertyName.TIME,
            PlayerType.PropertyName.TOTALTIME,
            PlayerType.PropertyName.REPEAT,
            PlayerType.PropertyName.SHUFFLED,
            PlayerType.PropertyName.CURRENTAUDIOSTREAM,
            PlayerType.PropertyName.CURRENTSUBTITLE,
            PlayerType.PropertyName.AUDIOSTREAMS,
            PlayerType.PropertyName.SUBTITLES,
            PlayerType.PropertyName.PLAYLISTID,
    };

    private static final String[] ITEM_FIELDS = {
            ListType.FieldsAll.ART,
            ListType.FieldsAll.ARTIST,
            ListType.FieldsAll.ALBUMARTIST,
            ListType.FieldsAll.ALBUM,
            ListType.FieldsAll.CAST,
            ListType.FieldsAll.DIRECTOR,
            ListType.FieldsAll.DISPLAYARTIST,
            ListType.FieldsAll.DURATION,
            ListType.FieldsAll.EPISODE,
            ListType.FieldsAll.FANART,
            ListType.FieldsAll.FILE,
            ListType.FieldsAll.FIRSTAIRED,
            ListType.FieldsAll.GENRE,
            ListType.FieldsAll.IMDBNUMBER,
            ListType.FieldsAll.PLOT,
            ListType.FieldsAll.PREMIERED,
            ListType.FieldsAll.RATING,
            ListType.FieldsAll.RESUME,
            ListType.FieldsAll.RUNTIME,
            ListType.FieldsAll.SEASON,
            ListType.FieldsAll.SHOWTITLE,
            ListType.FieldsAll.STREAMDETAILS,
            ListType.FieldsAll.STUDIO,
            ListType.FieldsAll.TAGLINE,
            ListType.FieldsAll.THUMBNAIL,
            ListType.FieldsAll.TITLE,
            ListType.FieldsAll.TOP250,
            ListType.FieldsAll.TRACK,
            ListType.FieldsAll.VOTES,
            ListType.FieldsAll.WRITER,
            ListType.FieldsAll.YEAR,
            ListType.FieldsAll.DESCRIPTION,
    };

    private final ScheduledExecutorService bg;
    private final LogLevel.Logger log;

    public ConnectionController(ScheduledExecutorService bg, LogLevel.Logger log) {
        this.bg = bg;
        this.log = log;
    }

    public Connection.Status connect(HostManager hosts, final State.Dispatcher fg) {
        final HostInfo preferredHost = hosts.getHostInfo();
        if (preferredHost == null) {
            return Connection.noHostsConfigured();
        }
        hosts.checkAndUpdateKodiVersion(preferredHost);
        String name = String.format("%s (%s)",
                preferredHost.getName(), preferredHost.getAddress());
        return Connection.connecting(name, bg.submit(new Callable<Connection.Status>() {
            @Override
            public Connection.Status call() throws ApiException {
                ApiClient client;
                if (preferredHost.getProtocol() == HostConnection.PROTOCOL_TCP) {
                    client = new TcpClient.Builder(preferredHost)
                            .withDispatcher(fg)
                            .withLogger(log)
                            .build();
                } else {
                    client = new HttpClient.Builder(preferredHost)
                            .withLogger(log)
                            .build();
                }
                return Connection.connected(client);
            }
        }));
    }

    public Connection.Status refresh(final ApiClient client) {
        return Connection.refreshing(bg.submit(new Callable<Connection.Status>() {
            @Override
            public Connection.Status call() throws ApiException, InterruptedException {
                ApplicationType.PropertyValue appProps = client
                        .send(new Application.GetProperties(APP_VOLUME));
                LogLevel.D.to(log, "Got volume info; level = %d, %s muted",
                        appProps.volume, appProps.muted ? "is" : "not");

                List<PlayerType.GetActivePlayersReturnType> players = client
                        .send(new Player.GetActivePlayers());
                LogLevel.D.to(log, "Got active players; size = %d", players.size());
                if (players.isEmpty()) {
                    return Connection.playerIsIdle(appProps.volume, appProps.muted);
                }

                PlayerType.GetActivePlayersReturnType player = players.get(0);
                PlayerType.PropertyValue playerProps = client
                        .send(new Player.GetProperties(player.playerid, PLAYER_PROPERTIES));
                LogLevel.D.to(log, "Got properties; speed = %d", playerProps.speed);
                ListType.ItemsAll itemInfo = client
                        .send(new Player.GetItem(player.playerid, ITEM_FIELDS));
                LogLevel.D.to(log, "Got item info; title = %s", itemInfo.title);
                return Connection.playerIsActive(
                        appProps.volume,
                        appProps.muted,
                        playerProps.speed == 0,
                        player,
                        playerProps,
                        itemInfo
                );
            }
        }));
    }

    public Connection.Status sync(ApiClient client, Connection.HostEvents on) {
        if (client.supports(ApiClient.FEATURE_NOTIFICATIONS)) {
            ConsolidateNotifications observer = new ConsolidateNotifications(on);
            return Connection.synced(Links.compose(
                    client.observePlayer(observer),
                    client.observeSystem(observer),
                    client.observeInput(observer),
                    client.observeApplication(observer)
            ));
        } else {
            final ScheduledFuture<?> future = bg
                    .scheduleWithFixedDelay(on, 3, 3, TimeUnit.SECONDS);
            return Connection.synced(new ApiClient.Link() {
                @Override
                public void unlink() {
                    future.cancel(false);
                }
            });
        }
    }

    public Connection.Status disconnect(final ApiClient client) {
        client.dispose();
        return Connection.disconnected();
    }
}
