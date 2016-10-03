package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Log;

public class RcPresenter implements Rc.Actions {

    private Rc.Ui view;
    private Rc.State state;
    private final Rc.UseCases will;

    public RcPresenter(Rc.UseCases will) {
        this.will = will;
    }

    @Override
    public void bind(Rc.Ui view) {
        this.view = view;
        will.restore(new Conventions.Just<Rc.State>() {
            @Override
            public void got(Rc.State state) {
                RcPresenter.this.state = state;
            }
        });
    }

    @Override
    public void unbind() {
        view = null;
        will.save(state);
    }

    @Override
    public void didPress(Rc.Button button) {
        Log.I.to(view, "pressed %s", button);
        switch (button) {
            case SELECT:
                view.tell("count: " + state.activePlayerId);
                break;
            case BACK:
                state.activePlayerId = 0;
                break;
        }
    }

    @Override
    public void didLongPress(Rc.Button button) {
        Log.I.to(view, "longpressed %s", button);
    }

    @Override
    public void didStartHoldingDown(Rc.Button button) {
        Log.I.to(view, "hold %s", button);
        view.showPressed(button);
        switch (button) {
            case UP:
                will.stop();
                will.fireAndFireAndFire("vol-inc", new Runnable() {
                    @Override
                    public void run() {
                        state.activePlayerId++;
                    }
                });
                break;
            case DOWN:
                will.stop();
                will.fireAndFireAndFire("vol-dec", new Runnable() {
                    @Override
                    public void run() {
                        state.activePlayerId--;
                    }
                });
                break;
        }
    }

    @Override
    public void didStopHoldingDown(Rc.Button button) {
        Log.I.to(view, "release %s", button);
        view.showNormal(button);
        switch (button) {
            case UP:
                will.stop("vol-inc");
                break;
            case DOWN:
                will.stop("vol-dec");
                break;
        }
    }

}
