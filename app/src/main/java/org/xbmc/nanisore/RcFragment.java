package org.xbmc.nanisore;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;

import org.xbmc.kore.R;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.utils.UIUtils;
import org.xbmc.nanisore.screens.rc.AndroidRcView;
import org.xbmc.nanisore.screens.rc.Rc;
import org.xbmc.nanisore.screens.rc.RcPresenter;
import org.xbmc.nanisore.utils.Lazy;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTouch;

public class RcFragment extends Fragment {

    private Rc.Actions user;
    private Rc.Ui view;

    public RcFragment() {}

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View rootView = inflater.inflate(R.layout.fragment_remote, container, false);
        Context context = getContext();
        HostManager hostManager = HostManager.getInstance(context);
        user = new RcPresenter();
        view = new AndroidRcView(context, hostManager.getPicasso());
        ButterKnife.inject(view, rootView);
        ButterKnife.inject(this, rootView);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        user.bind(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        user.unbind();
        view = null;
        ButterKnife.reset(this);
    }

    @OnTouch({R.id.select, R.id.back, R.id.info, R.id.osd, R.id.context})
    boolean touch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                this.view.animateIn(buttonOf(view.getId()));
                break;
            case MotionEvent.ACTION_UP:  // fallthrough
            case MotionEvent.ACTION_CANCEL:
                this.view.animateOut(buttonOf(view.getId()));
                break;
        }
        return false;
    }

    @OnClick({R.id.select, R.id.back, R.id.info, R.id.osd, R.id.context})
    void clickHaptic(View view) {
        UIUtils.handleVibration(getContext());
        click(view);
    }

    @OnClick({
            R.id.home, R.id.movies, R.id.tv_shows, R.id.music, R.id.pictures,
            R.id.fast_forward, R.id.rewind, R.id.play, R.id.stop,
    }) void click(View view) {
        user.didPress(buttonOf(view.getId()));
    }

    @OnLongClick(R.id.info)
    boolean longClick() {
        UIUtils.handleVibration(getContext());
        user.didLongPress(Rc.Button.INFO);
        return true;
    }

    @OnTouch({R.id.up, R.id.down, R.id.left, R.id.right})
    boolean longTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                UIUtils.handleVibration(getContext());
                user.didStartHoldingDown(buttonOf(view.getId()));
                break;
            case MotionEvent.ACTION_UP:
                view.playSoundEffect(SoundEffectConstants.CLICK);
                // fallthrough
            case MotionEvent.ACTION_CANCEL:
                user.didStopHoldingDown(buttonOf(view.getId()));
                break;
        }
        return true;
    }

    private static Rc.Button buttonOf(@IdRes int id) {
        switch (id) {
            case R.id.home: return Rc.Button.HOME;
            case R.id.movies: return Rc.Button.MOVIES;
            case R.id.tv_shows: return Rc.Button.SHOWS;
            case R.id.music: return Rc.Button.MUSIC;
            case R.id.pictures: return Rc.Button.PICTURES;
            case R.id.fast_forward: return Rc.Button.FORWARD;
            case R.id.rewind: return Rc.Button.REWIND;
            case R.id.play: return Rc.Button.PLAY;
            case R.id.stop: return Rc.Button.STOP;
            case R.id.select: return Rc.Button.SELECT;
            case R.id.back: return Rc.Button.BACK;
            case R.id.info: return Rc.Button.INFO;
            case R.id.osd: return Rc.Button.OSD;
            case R.id.context: return Rc.Button.CONTEXT;
            case R.id.up: return Rc.Button.UP;
            case R.id.down: return Rc.Button.DOWN;
            case R.id.left: return Rc.Button.LEFT;
            case R.id.right: return Rc.Button.RIGHT;
            default: return null;
        }
    }

}
