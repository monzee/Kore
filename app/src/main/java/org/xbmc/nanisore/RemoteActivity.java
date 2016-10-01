package org.xbmc.nanisore;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MenuItem;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.ui.BaseActivity;
import org.xbmc.kore.ui.NowPlayingFragment.NowPlayingListener;
import org.xbmc.kore.ui.SendTextDialogFragment.SendTextDialogListener;
import org.xbmc.nanisore.screens.AndroidOptions;
import org.xbmc.nanisore.screens.remote.AndroidRemoteKodiProxy;
import org.xbmc.nanisore.screens.remote.AndroidRemoteView;
import org.xbmc.nanisore.screens.remote.Remote;
import org.xbmc.nanisore.screens.remote.RemoteInteractor;
import org.xbmc.nanisore.screens.remote.RemotePresenter;
import org.xbmc.nanisore.utils.Lazy;
import org.xbmc.nanisore.utils.scheduling.AndroidLoaderRunner;

public class RemoteActivity extends BaseActivity
        implements SendTextDialogListener, NowPlayingListener
{
    private Remote.Actions user;
    private Remote.Ui view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Context context = getApplicationContext();
        HostManager hostManager = HostManager.getInstance(context);
        HostInfo hostInfo = hostManager.getHostInfo();
        AndroidRemoteKodiProxy rpc = new AndroidRemoteKodiProxy(
                hostManager.getConnection(),
                hostInfo
        );
        user = new RemotePresenter(
                new RemoteInteractor.Builder(new AndroidLoaderRunner(this), rpc)
                        .withHostPresent(hostInfo != null)
                        .withVideoToShare(sharedVideo())
                        .build(),
                rpc,
                new AndroidOptions(PreferenceManager.getDefaultSharedPreferences(context)),
                hostManager.getHostConnectionObserver()
        );
        view = new AndroidRemoteView(this, hostManager.getPicasso());
    }

    @Override
    protected void onStart() {
        super.onStart();
        user.bind(view);
    }

    @Override
    protected void onStop() {
        super.onStop();
        user.unbind();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        if (view.shouldInflateMenu()) {
            getMenuInflater().inflate(R.menu.remote, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Remote.Menu action = actionOf(item.getItemId());
        if (action != null) {
            user.didChoose(action);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (isDown) {
                    user.didPressVolumeUp();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (isDown) {
                    user.didPressVolumeDown();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public void onSendTextFinished(String text, boolean done) {
        int n = text.length();
        if (TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR.isRtl(text, 0, n)) {
            text = TextUtils.getReverse(text, 0, n).toString();
        }
        user.didSendText(text, done);
    }

    @Override
    public void onSendTextCancel() {
        // noop
    }

    @Override
    public void SwitchToRemotePanel() {
        view.switchTab(AndroidRemoteView.FRAGMENT_REMOTE);
    }

    @Override
    public void onShuffleClicked() {
        view.refreshPlaylist();
    }

    private String sharedVideo() {
        Intent i = getIntent();
        String action = i.getAction();
        if (action != null) {
            switch (action) {
                case Intent.ACTION_SEND:
                    String extra = i.getStringExtra(Intent.EXTRA_TEXT);
                    if (!TextUtils.isEmpty(extra)) {
                        return uriFromYoutubeShare(extra);
                    }
                    break;
                case Intent.ACTION_VIEW:
                    return i.getDataString();
            }
        }
        return null;
    }

    private String uriFromYoutubeShare(String extra) {
        for (String part : extra.split("\\s+")) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                return part;
            }
        }
        return null;
    }

    private static Remote.Menu actionOf(@IdRes int id) {
        switch (id) {
            case R.id.action_wake_up: return Remote.Menu.WAKE_UP;
            case R.id.action_quit: return Remote.Menu.QUIT;
            case R.id.action_suspend: return Remote.Menu.SUSPEND;
            case R.id.action_reboot: return Remote.Menu.REBOOT;
            case R.id.action_shutdown: return Remote.Menu.SHUTDOWN;
            case R.id.send_text: return Remote.Menu.SEND_TEXT;
            case R.id.toggle_fullscreen: return Remote.Menu.FULLSCREEN;
            case R.id.clean_video_library: return Remote.Menu.CLEAN_VIDEO_LIBRARY;
            case R.id.clean_audio_library: return Remote.Menu.CLEAN_AUDIO_LIBRARY;
            case R.id.update_video_library: return Remote.Menu.UPDATE_VIDEO_LIBRARY;
            case R.id.update_audio_library: return Remote.Menu.UPDATE_AUDIO_LIBRARY;
            default: return null;
        }
    }

}
