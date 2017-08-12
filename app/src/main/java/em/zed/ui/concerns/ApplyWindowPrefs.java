package em.zed.ui.concerns;

/*
 * This file is a part of the Kore project.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.preference.PreferenceManager;
import android.view.Window;
import android.view.WindowManager;

import org.xbmc.kore.R;
import org.xbmc.kore.Settings;

public class ApplyWindowPrefs extends Fragment {

    public interface CanShowWhenLocked {}

    public static int preferredTheme(Context context) {
        String preferredTheme = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(Settings.KEY_PREF_THEME, Settings.DEFAULT_PREF_THEME);
        switch (Integer.valueOf(preferredTheme)) {
            case 0:
                return R.style.NightTheme;
            case 1:
                return R.style.DayTheme;
            case 2:
                return R.style.MistTheme;
            case 3:
                return R.style.SolarizedLightTheme;
            case 4:
                return R.style.SolarizedDarkTheme;
            default:
                return R.style.NightTheme;
        }
    }

    public static void attach(FragmentManager fm) {
        Fragment f = fm.findFragmentByTag(TAG);
        if (f == null || !(f instanceof ApplyWindowPrefs)) {
            fm.beginTransaction().add(new ApplyWindowPrefs(), TAG).commit();
        }
    }

    private static final String TAG = ApplyWindowPrefs.class.getSimpleName();
    private SharedPreferences prefs;
    private boolean canApplyShowWhenLocked = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        canApplyShowWhenLocked = context instanceof CanShowWhenLocked;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (canApplyShowWhenLocked) {
            showWhenLocked(prefs.getBoolean(
                    Settings.KEY_PREF_KEEP_REMOTE_ABOVE_LOCKSCREEN,
                    Settings.DEFAULT_KEY_PREF_KEEP_REMOTE_ABOVE_LOCKSCREEN
            ));
        }
        keepScreenOn(prefs.getBoolean(
                Settings.KEY_PREF_KEEP_SCREEN_ON,
                Settings.DEFAULT_KEY_PREF_KEEP_SCREEN_ON
        ));
    }

    private void showWhenLocked(boolean on) {
        Window window = getActivity().getWindow();
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void keepScreenOn(boolean on) {
        Window window = getActivity().getWindow();
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

}
