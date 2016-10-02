package org.xbmc.nanisore.screens.remote;

import android.content.Intent;
import android.graphics.Point;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import org.xbmc.kore.R;
import org.xbmc.kore.service.ConnectionObserversManagerService;
import org.xbmc.kore.ui.NavigationDrawerFragment;
import org.xbmc.kore.ui.NowPlayingFragment;
import org.xbmc.kore.ui.PlaylistFragment;
import org.xbmc.kore.ui.SendTextDialogFragment;
import org.xbmc.kore.ui.hosts.AddHostActivity;
import org.xbmc.kore.ui.views.CirclePageIndicator;
import org.xbmc.kore.utils.TabsAdapter;
import org.xbmc.nanisore.RcFragment;
import org.xbmc.nanisore.screens.AndroidLogger;
import org.xbmc.nanisore.utils.values.Lazy;

import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class AndroidRemoteView extends AndroidLogger
        implements Remote.Ui, ViewPager.OnPageChangeListener
{
    private static final String TAG = AndroidRemoteView.class.getSimpleName();
    private static final int FRAGMENT_NOW_PLAYING = 0;
    public static final int FRAGMENT_REMOTE = 1;
    private static final int FRAGMENT_PLAYLIST = 2;
    private static final int[] TAB_TITLES = new int[] {
            R.string.now_playing, R.string.remote, R.string.playlist
    };

    private final Lazy<Point> displaySize = new Lazy<Point>() {
        @Override
        protected Point value() {
            Point value = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(value);
            return value;
        }
    };

    private final AppCompatActivity activity;
    private final FragmentManager fragments;
    private final Picasso picasso;
    private NavigationDrawerFragment navigationDrawerFragment;
    private boolean hasBackgroundImage;

    @InjectView(R.id.background_image) ImageView backgroundImage;
    @InjectView(R.id.pager_indicator) CirclePageIndicator pageIndicator;
    @InjectView(R.id.pager) ViewPager viewPager;
    @InjectView(R.id.default_toolbar) Toolbar toolbar;
    @InjectView(R.id.drawer_layout) DrawerLayout drawerLayout;

    public AndroidRemoteView(
            AppCompatActivity activity,
            Picasso picasso
    ) {
        super(activity.getApplicationContext(), TAG);
        this.activity = activity;
        this.picasso = picasso;
        fragments = activity.getSupportFragmentManager();
        ButterKnife.inject(this, activity);
        hasBackgroundImage = backgroundImage.getDrawable() != null;
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
            case IS_QUITTING:
                tell(getString(R.string.xbmc_quit));
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
    public void goToHostAdder() {
        Intent i = new Intent(activity, AddHostActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // TODO: this should request a result and not finish.
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
    public void initTabs(boolean fresh) {
        TabsAdapter tabsAdapter = new TabsAdapter(activity, fragments)
                .addTab(NowPlayingFragment.class, null, R.string.now_playing, FRAGMENT_NOW_PLAYING)
                .addTab(RcFragment.class, null, R.string.remote, FRAGMENT_REMOTE)
                .addTab(PlaylistFragment.class, null, R.string.playlist, FRAGMENT_PLAYLIST);
        viewPager.setAdapter(tabsAdapter);
        pageIndicator.setViewPager(viewPager);
        pageIndicator.setOnPageChangeListener(this);
        if (fresh) {
            viewPager.setCurrentItem(FRAGMENT_REMOTE);
            viewPager.setOffscreenPageLimit(2);
        }
    }

    @Override
    public void initActionBar() {
        setToolbarTitle(getString(TAB_TITLES[viewPager.getCurrentItem()]));
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
        promptTextToSend(getString(R.string.send_text));
    }

    @Override
    public void promptTextToSend(String prompt) {
        SendTextDialogFragment dlg = SendTextDialogFragment.newInstance(prompt);
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
            hasBackgroundImage = false;
        } else {
            Point size = displaySize.get();
            picasso.load(url)
                .resize(size.x, size.y / 2)
                .centerCrop()
                .into(backgroundImage);
            positionImage(backgroundImage.getViewTreeObserver(), size.x / 4);
            hasBackgroundImage = true;
        }
    }

    @Override
    public void startObservingPlayerStatus() {
        Intent i = new Intent(activity, ConnectionObserversManagerService.class);
        activity.startService(i);
    }

    @Override
    public void refreshPlaylist() {
        // TODO: redo this when the fragments are refactored
        String tag = "android:switcher:" + viewPager.getId() + ":3";
        PlaylistFragment playlistFragment = (PlaylistFragment) fragments
                .findFragmentByTag(tag);
        if (playlistFragment != null) {
            playlistFragment.forceRefreshPlaylist();
        }
    }

    @Override
    public void switchTab(int position) {
        viewPager.setCurrentItem(position);
    }

    @Override
    public void onPageScrolled(
            int position,
            float positionOffset,
            int positionOffsetPixels
    ) {
        if (hasBackgroundImage) {
            int pixelsPerPage = displaySize.get().x / 4;
            int offsetX = (int) ((position - 1 + positionOffset) * pixelsPerPage);
            backgroundImage.scrollTo(offsetX, 0);
        }
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

    private void positionImage(
            final ViewTreeObserver viewTreeObserver,
            final int pixelsPerPage
    ) {
        viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.removeOnPreDrawListener(this);
                }
                int offsetX = (viewPager.getCurrentItem() - 1) * pixelsPerPage;
                backgroundImage.scrollTo(offsetX, 0);
                return true;
            }
        });
    }

}
