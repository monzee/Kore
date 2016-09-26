package org.xbmc.kore.screens.remote;

import android.content.Intent;
import android.graphics.Point;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.ui.NavigationDrawerFragment;
import org.xbmc.kore.ui.NowPlayingFragment;
import org.xbmc.kore.ui.PlaylistFragment;
import org.xbmc.kore.ui.RemoteFragment;
import org.xbmc.kore.ui.SendTextDialogFragment;
import org.xbmc.kore.ui.hosts.AddHostActivity;
import org.xbmc.kore.ui.views.CirclePageIndicator;
import org.xbmc.kore.utils.TabsAdapter;
import org.xbmc.kore.utils.UIUtils;

import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.InjectView;
import com.squareup.picasso.Picasso;

public class RemoteView implements Remote.Display, ViewPager.OnPageChangeListener {

    private static final String TAG = "RemoteMvp";
    private static final int FRAGMENT_NOW_PLAYING = 0;
    private static final int FRAGMENT_REMOTE = 1;
    private static final int FRAGMENT_PLAYLIST = 2;
    private static final int[] TAB_TITLES = new int[] {
            R.string.now_playing, R.string.remote, R.string.playlist
    };

    private final AppCompatActivity activity;
    private final FragmentManager fragments;
    private final Picasso picasso;
    private NavigationDrawerFragment navigationDrawerFragment;

    @InjectView(R.id.background_image)
    ImageView backgroundImage;

    @InjectView(R.id.pager_indicator)
    CirclePageIndicator pageIndicator;

    @InjectView(R.id.pager)
    ViewPager viewPager;

    @InjectView(R.id.default_toolbar)
    Toolbar toolbar;

    @InjectView(R.id.drawer_layout)
    DrawerLayout drawerLayout;

    public RemoteView(AppCompatActivity activity, Picasso picasso) {
        this.activity = activity;
        this.picasso = picasso;
        fragments = activity.getSupportFragmentManager();
        ButterKnife.inject(this, activity);
    }

    @Override
    public void tell(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void tell(Remote.Message message, Object... fmtArgs) {
        switch (message) {
            case CANNOT_SHARE_VIDEO:
                tell(getString(R.string.error_share_video));
                break;
            case CANNOT_GET_ACTIVE_PLAYER:
                tell(getString(R.string.error_get_active_player, fmtArgs));
                break;
            case CANNOT_ENQUEUE_FILE:
                tell(getString(R.string.error_queue_media_file, fmtArgs));
                break;
            case CANNOT_PLAY_FILE:
                tell(getString(R.string.error_play_media_file, fmtArgs));
                break;
            default:
                if (fmtArgs.length == 1) {
                    tell(String.valueOf(fmtArgs[0]));
                } else {
                    tell(Arrays.toString(fmtArgs));
                }
                break;
        }
    }

    @Override
    public void log(Remote.Log level, String message) {
        switch (level) {
            case D:
                Log.d(TAG, message);
                break;
            case I:
                Log.i(TAG, message);
                break;
            case E:
                Log.e(TAG, message);
                break;
        }
    }

    @Override
    public void goToHostAdder() {
        Intent i = new Intent(activity, AddHostActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
        activity.finish();
    }

    @Override
    public void initNavigationDrawer() {
        navigationDrawerFragment = (NavigationDrawerFragment) fragments
                .findFragmentById(R.id.navigation_drawer);
        navigationDrawerFragment.setUp(R.id.navigation_drawer, drawerLayout);
    }

    @Override
    public void initTabs() {
        TabsAdapter tabsAdapter = new TabsAdapter(activity, fragments)
                .addTab(NowPlayingFragment.class, null, R.string.now_playing, FRAGMENT_NOW_PLAYING)
                .addTab(RemoteFragment.class, null, R.string.remote, FRAGMENT_REMOTE)
                .addTab(PlaylistFragment.class, null, R.string.playlist, FRAGMENT_PLAYLIST);
        viewPager.setAdapter(tabsAdapter);
        pageIndicator.setViewPager(viewPager);
        pageIndicator.setOnPageChangeListener(this);
        viewPager.setCurrentItem(FRAGMENT_REMOTE);
        viewPager.setOffscreenPageLimit(2);
    }

    @Override
    public void initActionBar() {
        setToolbarTitle(getString(TAB_TITLES[FRAGMENT_REMOTE]));
        activity.setSupportActionBar(toolbar);
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void toggleKeepAboveLockScreen(boolean enabled) {
        setWindowFlag(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, enabled);
    }

    @Override
    public void toggleKeepScreenOn(boolean enabled) {
        setWindowFlag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, enabled);
    }

    @Override
    public boolean shouldInflateMenu() {
        return navigationDrawerFragment == null || !navigationDrawerFragment.isDrawerOpen();
    }

    @Override
    public void promptTextToSend() {
        SendTextDialogFragment dlg = SendTextDialogFragment
                .newInstance(getString(R.string.send_text));
        dlg.show(fragments, null);
    }

    @Override
    public void setToolbarTitle(String title) {
        toolbar.setTitle(title);
    }

    @Override
    public void setBackgroundImage(String url) {
        if (url == null) {
            backgroundImage.setImageDrawable(null);
            pageIndicator.setOnPageChangeListener(this);
        } else {
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            picasso.load(url)
                .resize(displaySize.x, displaySize.y / 2)
                .centerCrop()
                .into(backgroundImage);

            pinBackground(backgroundImage.getViewTreeObserver(), displaySize.x / 4);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // noop
    }

    @Override
    public void onPageSelected(int position) {
        setToolbarTitle(getString(TAB_TITLES[position]));
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // noop
    }

    private void setWindowFlag(int flag, boolean enabled) {
        if (enabled) {
            activity.getWindow().addFlags(flag);
        } else {
            activity.getWindow().clearFlags(flag);
        }
    }

    private void pinBackground(final ViewTreeObserver viewTreeObserver, final int pixelsPerPage) {
        viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                viewTreeObserver.removeOnPreDrawListener(this);
                // Position the image
                int offsetX =  (viewPager.getCurrentItem() - 1) * pixelsPerPage;
                backgroundImage.scrollTo(offsetX, 0);

                pageIndicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(
                            int position, float positionOffset, int positionOffsetPixels
                    ) {
                        int offsetX = (int) ((position - 1 + positionOffset) * pixelsPerPage);
                        backgroundImage.scrollTo(offsetX, 0);
                    }

                    @Override
                    public void onPageSelected(int position) {
                        setToolbarTitle(getString(TAB_TITLES[position]));
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {
                        // noop
                    }
                });
                return true;
            }
        });
    }

    private String getString(int resId, Object... fmtArgs) {
        return activity.getString(resId, fmtArgs);
    }

}
