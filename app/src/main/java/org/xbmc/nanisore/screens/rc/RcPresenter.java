package org.xbmc.nanisore.screens.rc;

import org.xbmc.nanisore.screens.Conventions;
import org.xbmc.nanisore.utils.Log;

public class RcPresenter implements Rc.Actions {

    private Rc.Ui view;
    private Rc.State state;
    private final Rc.UseCases will;
    private final Rc.Rpc kodi;

    public RcPresenter(Rc.UseCases will, Rc.Rpc kodi) {
        this.will = will;
        this.kodi = kodi;
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
    public void didPress(final Rc.Button button) {
        Log.I.to(view, "pressed %s", button);
        will.fireAndLogTo(view, new Runnable() {
            @Override
            public void run() {
                switch (button) {
                    case SELECT:
                        kodi.trySelect();
                        break;
                    case BACK:
                        kodi.tryBack();
                        break;
                    case INFO:
                        kodi.tryInfo();
                        break;
                    case CONTEXT:
                        kodi.tryContextMenu();
                        break;
                    case OSD:
                        kodi.tryOsd();
                        break;
                    case HOME:
                        kodi.tryHome();
                        break;
                    case MOVIES:
                        kodi.tryMovies();
                        break;
                    case SHOWS:
                        kodi.tryShows();
                        break;
                    case MUSIC:
                        kodi.tryMusic();
                        break;
                    case PICTURES:
                        kodi.tryPictures();
                        break;
                }
            }
        });
    }

    @Override
    public void didLongPress(Rc.Button button) {
        Log.I.to(view, "longpressed %s", button);
        if (button == Rc.Button.INFO) {
            will.fireAndLogTo(view, new Runnable() {
                @Override
                public void run() {
                    kodi.tryCodecInfo();
                }
            });
        }
    }

    @Override
    public void didStartHoldingDown(Rc.Button button) {
        Log.I.to(view, "hold %s", button);
        will.stop();
        view.showPressed(button);
        switch (button) {
            case UP:
                will.fireRepeatedly("up", new Runnable() {
                    @Override
                    public void run() {
                        kodi.tryUp(false);
                    }
                });
                break;
            case DOWN:
                will.fireRepeatedly("down", new Runnable() {
                    @Override
                    public void run() {
                        kodi.tryDown(false);
                    }
                });
                break;
            case LEFT:
                will.fireRepeatedly("left", new Runnable() {
                    @Override
                    public void run() {
                        kodi.tryLeft(false);
                    }
                });
                break;
            case RIGHT:
                will.fireRepeatedly("right", new Runnable() {
                    @Override
                    public void run() {
                        kodi.tryRight(false);
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
                will.stop("up");
                break;
            case DOWN:
                will.stop("down");
                break;
            case LEFT:
                will.stop("left");
                break;
            case RIGHT:
                will.stop("right");
                break;
        }
    }

}
