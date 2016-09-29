package org.xbmc.nanisore;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.squareup.picasso.Picasso;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.ui.BaseActivity;
import org.xbmc.kore.ui.NowPlayingFragment.NowPlayingListener;
import org.xbmc.kore.ui.SendTextDialogFragment.SendTextDialogListener;
import org.xbmc.nanisore.screens.AndroidOptions;
import org.xbmc.nanisore.screens.remote.AndroidRemoteHostProxy;
import org.xbmc.nanisore.screens.remote.AndroidRemoteView;
import org.xbmc.nanisore.screens.remote.Remote;
import org.xbmc.nanisore.screens.remote.Remote.MenuAction;
import org.xbmc.nanisore.screens.remote.RemoteInteractor;
import org.xbmc.nanisore.screens.remote.RemotePresenter;
import org.xbmc.nanisore.utils.Lazy;
import org.xbmc.nanisore.utils.scheduling.AndroidLoaderRunner;

public class RemoteActivity extends BaseActivity
        implements SendTextDialogListener, NowPlayingListener
{
    private static final
    Lazy<SparseArray<MenuAction>> menuActions = new Lazy<SparseArray<MenuAction>>() {
        @Override
        protected SparseArray<MenuAction> value() {
            SparseArray<MenuAction> map = new SparseArray<>();
            map.append(R.id.action_wake_up, MenuAction.WAKE_UP);
            map.append(R.id.action_quit, MenuAction.QUIT);
            map.append(R.id.action_suspend, MenuAction.SUSPEND);
            map.append(R.id.action_reboot, MenuAction.REBOOT);
            map.append(R.id.action_shutdown, MenuAction.SHUTDOWN);
            map.append(R.id.send_text, MenuAction.SEND_TEXT);
            map.append(R.id.toggle_fullscreen, MenuAction.FULLSCREEN);
            map.append(R.id.clean_video_library, MenuAction.CLEAN_VIDEO_LIBRARY);
            map.append(R.id.clean_audio_library, MenuAction.CLEAN_AUDIO_LIBRARY);
            map.append(R.id.update_video_library, MenuAction.UPDATE_VIDEO_LIBRARY);
            map.append(R.id.update_audio_library, MenuAction.UPDATE_AUDIO_LIBRARY);
            return map;
        }
    };

    private Remote.Actions user;
    private Remote.Ui view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Context context = getApplicationContext();
        HostManager hostManager = HostManager.getInstance(context);
        HostInfo hostInfo = hostManager.getHostInfo();
        AndroidRemoteHostProxy rpc = new AndroidRemoteHostProxy(
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
        view = new AndroidRemoteView(this, Picasso.with(context));
    }

    @Override
    protected void onResume() {
        super.onResume();
        user.bind(view);
    }

    @Override
    protected void onStop() {
        super.onStop();
        user.unbind();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (view.shouldInflateMenu()) {
            getMenuInflater().inflate(R.menu.remote, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        MenuAction action = menuActions.get().get(item.getItemId());
        if (action != null) {
            user.didChoose(action);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
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

}
