package org.xbmc.nanisore;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

}
