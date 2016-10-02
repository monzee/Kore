package org.xbmc.nanisore.screens.rc;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.xbmc.kore.R;
import org.xbmc.kore.utils.CharacterDrawable;
import org.xbmc.kore.utils.UIUtils;
import org.xbmc.nanisore.screens.AndroidLogger;
import org.xbmc.nanisore.utils.values.Lazy;

import butterknife.InjectView;
import butterknife.InjectViews;

public class AndroidRcView extends AndroidLogger implements Rc.Ui {

    private static final String TAG = AndroidRcView.class.getSimpleName();

    /**
     * The values of the following ints should match the array indexes
     * of the corresponding attrs in {@link #icons}
     */
    private static final int FORWARD = 0;
    private static final int REWIND = 1;
    private static final int NEXT = 2;
    private static final int PREV = 3;
    private static final int PLAY = 4;
    private static final int PAUSE = 5;

    /**
     * The values of the following ints should match the array indexes
     * of the corresponding Animation in {@link #animations}
     */
    private static final int BUTTON_IN = 0;
    private static final int BUTTON_OUT = 1;

    private final Lazy<Integer[]> icons = new Lazy<Integer[]>() {
        @Override
        protected Integer[] value() {
            // the order of the elements should match the int constants above
            TypedArray xs = context.getTheme().obtainStyledAttributes(new int[] {
                    R.attr.iconFastForward, R.attr.iconRewind,
                    R.attr.iconNext, R.attr.iconPrevious,
                    R.attr.iconPlay, R.attr.iconPause,
            });
            // likewise
            Integer[] fallback = new Integer[] {
                    R.drawable.ic_fast_forward_white_24dp,
                    R.drawable.ic_fast_rewind_white_24dp,
                    R.drawable.ic_skip_next_white_24dp,
                    R.drawable.ic_skip_previous_white_24dp,
                    R.drawable.ic_play_arrow_white_24dp,
                    R.drawable.ic_pause_white_24dp
            };
            for (int i = 0; i < fallback.length; i++) {
                fallback[i] = xs.getResourceId(xs.getIndex(i), fallback[i]);
            }
            xs.recycle();
            return fallback;
        }
    };

    private final Lazy<Animation[]> animations = new Lazy<Animation[]>() {
        @Override
        protected Animation[] value() {
            // the order of the elements should match the BUTTON_* constants
            Animation[] xs = new Animation[] {
                    AnimationUtils.loadAnimation(context, R.anim.button_in),
                    AnimationUtils.loadAnimation(context, R.anim.button_out)
            };
            xs[0].setFillAfter(true);
            return xs;
        }
    };

    private final Context context;
    private final Picasso picasso;

    @InjectView(R.id.info_panel) RelativeLayout boxInfo;
    @InjectView(R.id.media_panel) RelativeLayout boxMedia;
    @InjectView(R.id.remote) RelativeLayout boxRemote;
    @InjectView(R.id.button_bar) LinearLayout boxButtonBar;

    @InjectView(R.id.info_title) TextView theInfoHead;
    @InjectView(R.id.info_message) TextView theInfoBody;

    @InjectView(R.id.title) TextView theTitle;
    @InjectView(R.id.details) TextView theDetails;
    @InjectView(R.id.art) ImageView theThumbnail;

    @InjectView(R.id.play) ImageButton doPlay;
    @InjectView(R.id.rewind) ImageButton doRewind;
    @InjectView(R.id.fast_forward) ImageButton doFastForward;

    /**
     * The order of the IDs should match the order of the enum branches
     * in {@link #indexOf}
     */
    @InjectViews({
            R.id.up, R.id.down, R.id.left, R.id.right,
            R.id.select, R.id.back, R.id.info, R.id.osd, R.id.context,
    }) View[] animatedButtons;

    public AndroidRcView(Context context, Picasso picasso) {
        super(context, TAG);
        this.context = context;
        this.picasso = picasso;
    }

    @Override
    public void togglePlayPauseIcon(boolean showPlay) {
        doPlay.setImageResource(icons.get()[showPlay ? PLAY : PAUSE]);
    }

    @Override
    public void toggleMediaInfoPanel(boolean showMedia) {
        if (showMedia) {
            boxInfo.setVisibility(View.GONE);
            boxMedia.setVisibility(View.VISIBLE);
        } else {
            boxInfo.setVisibility(View.VISIBLE);
            boxMedia.setVisibility(View.GONE);
        }
    }

    @Override
    public void toggleRemotePanel(boolean visible) {
        if (visible) {
            boxRemote.setVisibility(View.VISIBLE);
            boxButtonBar.setVisibility(View.VISIBLE);
        } else {
            boxRemote.setVisibility(View.GONE);
            boxButtonBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void showPressed(Rc.Button button) {
        int i = indexOf(button);
        if (i != -1) {
            animatedButtons[i].startAnimation(animations.get()[BUTTON_IN]);
        }
    }

    @Override
    public void showNormal(Rc.Button button) {
        int i = indexOf(button);
        if (i != -1) {
            animatedButtons[i].startAnimation(animations.get()[BUTTON_OUT]);
        }
    }

    @Override
    public void say(String message, Object... fmtArgs) {
        theInfoHead.setText(String.format(message, fmtArgs));
    }

    @Override
    public void mumble(String message, Object... fmtArgs) {
        theInfoBody.setText(String.format(message, fmtArgs));
    }

    @Override
    public void show(
            String title,
            String details,
            String thumbnail,
            boolean showSkipIcons
    ) {
        theTitle.setText(title);
        theDetails.setText(details);

        Integer[] iconOf = icons.get();
        doRewind.setImageResource(iconOf[showSkipIcons ? PREV : REWIND]);
        doFastForward.setImageResource(iconOf[showSkipIcons ? NEXT : FORWARD]);

        CharacterDrawable avatar = UIUtils.getCharacterAvatar(context, title);
        int width = theThumbnail.getWidth();
        int height = theThumbnail.getHeight();

        if (TextUtils.isEmpty(thumbnail)) {
            theThumbnail.setImageDrawable(avatar);
        } else if (width > 0 && height > 0) {
            picasso.load(thumbnail)
                    .placeholder(avatar)
                    .resize(width, height)
                    .centerCrop()
                    .into(theThumbnail);
        } else {
            picasso.load(thumbnail).fit().centerCrop().into(theThumbnail);
        }
    }

    /**
     * The order should match the list of IDs in {@link #animatedButtons}
     */
    private static int indexOf(Rc.Button button) {
        switch (button) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            case SELECT: return 4;
            case BACK: return 5;
            case INFO: return 6;
            case OSD: return 7;
            case CONTEXT: return 8;
            default: return -1;
        }
    }

}
