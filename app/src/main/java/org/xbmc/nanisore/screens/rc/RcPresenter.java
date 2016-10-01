package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.utils.Log;

public class RcPresenter implements Rc.Actions {

    private Rc.Ui view;

    @Override
    public void bind(Rc.Ui view) {
        this.view = view;
    }

    @Override
    public void unbind() {
        view = null;
    }

    @Override
    public void didPress(Rc.Button button) {
        Log.I.to(view, "pressed %s", button);
    }

    @Override
    public void didLongPress(Rc.Button button) {
        Log.I.to(view, "longpressed %s", button);
    }

    @Override
    public void didStartHoldingDown(Rc.Button button) {
        Log.I.to(view, "hold %s", button);
        view.animateIn(button);
    }

    @Override
    public void didStopHoldingDown(Rc.Button button) {
        Log.I.to(view, "release %s", button);
        view.animateOut(button);
    }

}
