package org.xbmc.nanisore;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;

import com.squareup.picasso.Picasso;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.ui.BaseActivity;
import org.xbmc.nanisore.screens.AndroidOptions;
import org.xbmc.nanisore.screens.remote.AndroidRemoteHostProxy;
import org.xbmc.nanisore.screens.remote.AndroidRemoteView;
import org.xbmc.nanisore.screens.remote.Remote;
import org.xbmc.nanisore.screens.remote.RemoteInteractor;
import org.xbmc.nanisore.screens.remote.RemotePresenter;
import org.xbmc.nanisore.utils.scheduling.AndroidLoaderRunner;

public class RemoteActivity extends BaseActivity {

    private Remote.Actions presenter;
    private Remote.Ui view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Context context = getApplicationContext();
        HostManager hostManager = HostManager.getInstance(context);
        AndroidRemoteHostProxy rpc = new AndroidRemoteHostProxy(hostManager.getConnection());
        presenter = new RemotePresenter(
                hostManager,
                new RemoteInteractor(new AndroidLoaderRunner(this), rpc),
                rpc,
                new AndroidOptions(PreferenceManager.getDefaultSharedPreferences(context))
        );
        view = new AndroidRemoteView(presenter, this, Picasso.with(context));
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.bind(view);
        Intent i = getIntent();
        String action = i.getAction();
        if (action != null) {
            switch (action) {
                case Intent.ACTION_SEND:
                    String extra = i.getStringExtra(Intent.EXTRA_TEXT);
                    if (extra != null) {
                        presenter.didShareVideo(uriFromYoutubeShare(extra));
                    }
                    break;
                case Intent.ACTION_VIEW:
                    String data = i.getDataString();
                    if (data != null) {
                        presenter.didShareVideo(data);
                    }
                    break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        presenter.unbind();
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                presenter.didPressVolumeUp();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                presenter.didPressVolumeDown();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private String uriFromYoutubeShare(String extra) {
        String[] parts = extra.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                return part;
            }
        }
        return "";
    }

}
