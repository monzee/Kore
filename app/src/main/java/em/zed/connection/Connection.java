package em.zed.connection;

/*
 * This file is a part of the Kore project.
 */

import org.xbmc.kore.jsonrpc.notification.Player;
import org.xbmc.kore.jsonrpc.type.GlobalType;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;

import java.util.concurrent.Future;

import em.zed.backend.ApiClient;
import em.zed.util.State;

public final class Connection {

    private Connection() {
    }

    /**
     * @see Port#noHostsConfigured()
     */
    public static Status noHostsConfigured() {
        return new Status("no-hosts-configured") {
            @Override
            public void apply(Port actor) {
                actor.noHostsConfigured();
            }
        };
    }

    /**
     * @see Port#connecting(String, Future)
     */
    public static Status connecting(final String hostName, final Future<Status> result) {
        return new Status("connecting") {
            @Override
            public void apply(Port actor) {
                actor.connecting(hostName, result);
            }
        };
    }

    /**
     * @see Port#connected(ApiClient)
     */
    public static Status connected(final ApiClient client) {
        return new Status("connected") {
            @Override
            public void apply(Port actor) {
                actor.connected(client);
            }
        };
    }

    /**
     * @see Port#refreshing(Future)
     */
    public static Status refreshing(final Future<Status> result) {
        return new Status("refreshing") {
            @Override
            public void apply(Port actor) {
                actor.refreshing(result);
            }
        };
    }

    /**
     * @see Port#playerIsIdle(int, boolean)
     */
    public static Status playerIsIdle(final int volume, final boolean isMuted) {
        return new Status("player-is-idle") {
            @Override
            public void apply(Port actor) {
                actor.playerIsIdle(volume, isMuted);
            }
        };
    }

    /**
     * @see Port#playerIsActive(int, boolean, boolean,
     * PlayerType.GetActivePlayersReturnType, PlayerType.PropertyValue,
     * ListType.ItemsAll)
     */
    public static Status playerIsActive(
            final int volume,
            final boolean isMuted,
            final boolean isPlaying,
            final PlayerType.GetActivePlayersReturnType player,
            final PlayerType.PropertyValue properties,
            final ListType.ItemsAll itemInfo
    ) {
        return new Status("player-is-active") {
            @Override
            public void apply(Port actor) {
                actor.playerIsActive(volume, isMuted, isPlaying, player, properties, itemInfo);
            }
        };
    }

    /**
     * @see Port#synced(ApiClient.Link)
     */
    public static Status synced(final ApiClient.Link link) {
        return new Status("synced") {
            @Override
            public void apply(Port actor) {
                actor.synced(link);
            }
        };
    }

    /**
     * @see Port#disconnected()
     */
    public static Status disconnected() {
        return new Status("disconnected") {
            @Override
            public void apply(Port actor) {
                actor.disconnected();
            }
        };
    }

    public interface Port {
        /**
         * The initial state. Should schedule to connect on next dispatch.
         */
        void disconnected();

        /**
         * No known hosts; should display the add host screen.
         */
        void noHostsConfigured();

        /**
         * Should show an in-progress indicator.
         *
         * @param hostName name of the host we are connecting to
         * @param result   will lead to the {@link #connected} state or throw an
         *                 {@link org.xbmc.kore.jsonrpc.ApiException} if a TCP
         *                 connection cannot be established. Will never throw for
         *                 HTTP-connected hosts.
         */
        void connecting(String hostName, Future<Status> result);

        /**
         * A good place to pull the host's current info and save a reference to
         * the {@link ApiClient}. This should be the "idle" state of the
         * system so that when it is resumed in this state, all remote state
         * will be refreshed and re-synced.
         *
         * @param client either a {@link em.zed.backend.TcpClient}
         *               or {@link em.zed.backend.HttpClient} depending on the
         *               host settings.
         */
        void connected(ApiClient client);

        /**
         * Should show an in-progress indicator.
         *
         * @param result will lead to either {@link #playerIsIdle}, {@link #playerIsActive}
         *               or throws an {@link org.xbmc.kore.jsonrpc.ApiException}
         *               if one of the fetch calls fails.
         */
        void refreshing(Future<Status> result);

        /**
         * A good place to initiate synchronization. Should hide the in-progress
         * indicator.
         *
         * @param volume  the current volume level
         * @param isMuted whether the player is muted or not
         */
        void playerIsIdle(int volume, boolean isMuted);

        /**
         * A good place to initiate synchronization. Should hide the in-progress
         * indicator.
         *
         * @param volume     the current volume level
         * @param isMuted    the muted state
         * @param isPlaying  if the player is currently playing
         * @param player     contains the player id and type
         * @param properties the whole player state
         * @param itemInfo   currently playing item metadata
         */
        void playerIsActive(
                int volume,
                boolean isMuted,
                boolean isPlaying,
                PlayerType.GetActivePlayersReturnType player,
                PlayerType.PropertyValue properties,
                ListType.ItemsAll itemInfo
        );

        /**
         * LEAK ALERT: the system should not linger in this state.
         * <p>
         * The {@link ApiClient.Link} object would inevitably have a reference
         * to an actor instance. The actor is kept alive while the machine is in
         * this state. You should call {@link State.Machine#willApply(State.Action)}
         * to somwhere else unconditionally inside this method. You should also
         * make sure to call {@link ApiClient.Link#unlink()} eventually and null
         * out any copies of this reference when you stop the machine.
         *
         * @param link stops the sync/polling when unlinked
         */
        void synced(ApiClient.Link link);
    }

    public interface HostEvents extends Runnable {
        void playerStateChanged(
                int playerId,
                int speed,
                boolean shuffled,
                String repeatMode,
                Player.NotificationsItem info
        );

        void playerPositionChanged(
                GlobalType.Time position,
                GlobalType.Time duration,
                Player.NotificationsItem info
        );

        void playerStopped(boolean ended, Player.NotificationsItem info);

        void inputRequested(String title, String type, String value);

        void appVolumeChanged(int volume, boolean muted);

        void aboutToExit();
    }

    /**
     * Sealed class; no other subtypes of this type can be created elsewhere
     * because of the private constructor.
     */
    public static abstract class Status implements State.Action<Port> {
        private final String repr;

        private Status(String repr) {
            this.repr = repr;
        }

        @Override
        public String toString() {
            return repr;
        }
    }
}
