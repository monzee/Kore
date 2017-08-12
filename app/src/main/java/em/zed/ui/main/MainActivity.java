package em.zed.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.notification.Player;
import org.xbmc.kore.jsonrpc.type.GlobalType;
import org.xbmc.kore.jsonrpc.type.ListType;
import org.xbmc.kore.jsonrpc.type.PlayerType;
import org.xbmc.kore.ui.sections.hosts.AddHostActivity;
import org.xbmc.kore.ui.views.CirclePageIndicator;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import em.zed.Globals;
import em.zed.backend.ApiClient;
import em.zed.connection.Connection;
import em.zed.connection.ConnectionController;
import em.zed.ui.concerns.ApplyWindowPrefs;
import em.zed.util.AndroidLogger;
import em.zed.util.LogLevel;
import em.zed.util.State;
import em.zed.util.UiDispatcher;


/*
 * This file is a part of the Kore project.
 */

public class MainActivity extends AppCompatActivity
        implements Connection.Port,
        Connection.HostEvents,
        ApplyWindowPrefs.CanShowWhenLocked {

    private static class Scope {
        final LogLevel.Logger log = new AndroidLogger("main-activity");
        final UiDispatcher dispatcher = new UiDispatcher() {
            @Override
            public void handle(Throwable error) {
                LogLevel.E.to(log, error, "Error while dispatching an action");
                throw new RuntimeException(error);
            }
        };
        final State.Machine<Connection.Status, Connection.Port> connector = new State.Machine<>(
                Globals.DEFAULT.threadContext("main-junction-thread"),
                dispatcher
        );
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final ConnectionController connection = new ConnectionController(scheduler, log);
        ApiClient client = ApiClient.DISCONNECTED;
        Connection.Status idleState = Connection.disconnected();
    }

    private Scope our;
    private ApiClient.Link syncHandle;
    private Future<?> retry;

    @InjectView(R.id.background_image) ImageView backgroundImage;
    @InjectView(R.id.pager_indicator) CirclePageIndicator pageIndicator;
    @InjectView(R.id.pager) ViewPager viewPager;
    @InjectView(R.id.default_toolbar) Toolbar toolbar;

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return our;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        our = (Scope) getLastCustomNonConfigurationInstance();
        if (our == null) {
            our = new Scope();
            our.connector.apply(our.idleState, this);
        }
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setTheme(ApplyWindowPrefs.preferredTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        ButterKnife.inject(this);
        ApplyWindowPrefs.attach(getSupportFragmentManager());
    }

    @Override
    protected void onResume() {
        super.onResume();
        our.connector.dispatch(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        our.connector.stop();
        if (retry != null) {
            retry.cancel(false);
            retry = null;
        }
        if (syncHandle != null) {
            syncHandle.unlink();
            syncHandle = null;
        }
        if (isFinishing()) {
            our.client.dispose();
        }
    }

    @Override
    public void disconnected() {
        our.connector.willApply(our.connection.connect(
                HostManager.getInstance(this),
                our.dispatcher
        ));
    }

    @Override
    public void noHostsConfigured() {
        disconnected();
        Intent intent = new Intent(this, AddHostActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void connecting(String hostName, Future<Connection.Status> result) {
        our.connector.await(result, this, new State.Catch() {
            @Override
            public void handle(Throwable error) {
                LogLevel.E.to(our.log, error, "TODO: show error message");
                disconnected();
            }
        });
    }

    @Override
    public void connected(ApiClient client) {
        our.client = client;
        our.idleState = our.connector.peek();
        run();
    }

    @Override
    public void refreshing(Future<Connection.Status> result) {
        our.connector.await(result, this, new State.Catch() {
            @Override
            public void handle(Throwable error) {
                LogLevel.E.to(our.log, error, "TODO: show error message");
                retry = our.scheduler.schedule(MainActivity.this, 10, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public void playerIsIdle(int volume, boolean isMuted) {
        if (syncHandle == null) {
            our.connector.apply(our.connection.sync(our.client, this), this);
        }
    }

    @Override
    public void playerIsActive(
            int volume,
            boolean isMuted,
            boolean isPlaying,
            PlayerType.GetActivePlayersReturnType player,
            PlayerType.PropertyValue properties,
            ListType.ItemsAll itemInfo
    ) {
        if (syncHandle == null) {
            our.connector.apply(our.connection.sync(our.client, this), this);
        }
    }

    @Override
    public void synced(ApiClient.Link link) {
        syncHandle = link;
        our.connector.willApply(our.idleState);
    }

    @Override
    public void playerStateChanged(
            int playerId, int speed, boolean shuffled,
            String repeatMode, Player.NotificationsItem info
    ) {
        run();
    }

    @Override
    public void playerPositionChanged(
            GlobalType.Time position, GlobalType.Time duration,
            Player.NotificationsItem info
    ) {
        run();
    }

    @Override
    public void playerStopped(boolean ended, Player.NotificationsItem info) {
        run();
    }

    @Override
    public void inputRequested(String title, String type, String value) {

    }

    @Override
    public void appVolumeChanged(int volume, boolean muted) {

    }

    @Override
    public void aboutToExit() {

    }

    @Override
    public void run() {
        our.connector.apply(our.connection.refresh(our.client), MainActivity.this);
    }

}
