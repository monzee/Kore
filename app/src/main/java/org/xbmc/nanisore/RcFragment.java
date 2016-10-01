package org.xbmc.nanisore;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

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

    private static final
    Lazy<SparseArray<Rc.Button>> BUTTONS = new Lazy<SparseArray<Rc.Button>>() {
        @Override
        protected SparseArray<Rc.Button> value() {
            SparseArray<Rc.Button> map = new SparseArray<>();
            map.append(R.id.home, Rc.Button.HOME);
            map.append(R.id.movies, Rc.Button.MOVIES);
            map.append(R.id.tv_shows, Rc.Button.SHOWS);
            map.append(R.id.music, Rc.Button.MUSIC);
            map.append(R.id.pictures, Rc.Button.PICTURES);
            map.append(R.id.fast_forward, Rc.Button.FORWARD);
            map.append(R.id.rewind, Rc.Button.REWIND);
            map.append(R.id.play, Rc.Button.PLAY);
            map.append(R.id.stop, Rc.Button.STOP);
            map.append(R.id.select, Rc.Button.SELECT);
            map.append(R.id.back, Rc.Button.BACK);
            map.append(R.id.info, Rc.Button.INFO);
            map.append(R.id.osd, Rc.Button.OSD);
            map.append(R.id.context, Rc.Button.CONTEXT);
            map.append(R.id.up, Rc.Button.UP);
            map.append(R.id.down, Rc.Button.DOWN);
            map.append(R.id.left, Rc.Button.LEFT);
            map.append(R.id.right, Rc.Button.RIGHT);
            return map;
        }
    };

    private static final int BUTTON_IN = 0;
    private static final int BUTTON_OUT = 1;

    private final Lazy<Animation[]> animations = new Lazy<Animation[]>() {
        @Override
        protected Animation[] value() {
            Context context = getContext();
            Animation[] xs = new Animation[] {
                    AnimationUtils.loadAnimation(context, R.anim.button_in),
                    AnimationUtils.loadAnimation(context, R.anim.button_out)
            };
            xs[0].setFillAfter(true);
            return xs;
        }
    };

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
        ButterKnife.inject(view, rootView);
        ButterKnife.inject(this, rootView);
        user.bind(view);
        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        HostManager hostManager = HostManager.getInstance(context);
        view = new AndroidRcView(context, hostManager.getPicasso());
        user = new RcPresenter();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        user.unbind();
        view = null;
        ButterKnife.reset(this);
    }

    @OnTouch({R.id.select, R.id.back, R.id.info, R.id.osd, R.id.context})
    boolean touch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                view.startAnimation(animations.get()[BUTTON_IN]);
                break;
            case MotionEvent.ACTION_UP:  // fallthrough
            case MotionEvent.ACTION_CANCEL:
                view.startAnimation(animations.get()[BUTTON_OUT]);
                break;
        }
        return false;
    }

    @OnTouch({R.id.up, R.id.down, R.id.left, R.id.right})
    boolean longTouch(View view, MotionEvent event) {
        Rc.Button button = BUTTONS.get().get(view.getId());
        Animation[] animate = animations.get();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                UIUtils.handleVibration(getContext());
                user.didStartHoldingDown(button);
                view.startAnimation(animate[BUTTON_IN]);
                break;
            case MotionEvent.ACTION_UP:
                view.playSoundEffect(SoundEffectConstants.CLICK);
                // fallthrough
            case MotionEvent.ACTION_CANCEL:
                user.didStopHoldingDown(button);
                view.startAnimation(animate[BUTTON_OUT]);
                break;
        }
        return true;
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
        user.didPress(BUTTONS.get().get(view.getId()));
    }

    @OnLongClick(R.id.info)
    boolean longClick() {
        UIUtils.handleVibration(getContext());
        user.didLongPress(Rc.Button.INFO);
        return true;
    }

}
