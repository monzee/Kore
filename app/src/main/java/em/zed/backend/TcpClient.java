package em.zed.backend;

/*
 * This file is a part of the Kore project.
 */

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.jsonrpc.ApiException;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.notification.Application;
import org.xbmc.kore.jsonrpc.notification.Input;
import org.xbmc.kore.jsonrpc.notification.Player;
import org.xbmc.kore.jsonrpc.notification.System;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import em.zed.Globals;
import em.zed.util.LogLevel;
import em.zed.util.State;

public class TcpClient implements ApiClient, Runnable {

    private final State.Dispatcher fg;
    private final LogLevel.Logger log;
    private final Socket socket;
    private final InputStream input;
    private final BufferedWriter output;
    private final Future<?> watchResponse;
    private final Map<Integer, Result<String>> pending = new TreeMap<>();
    private final Set<OnPlayerNotification> playerObservers = new HashSet<>();
    private final Set<OnSystemNotification> systemObservers = new HashSet<>();
    private final Set<OnInputNotification> inputObservers = new HashSet<>();
    private final Set<OnApplicationNotification> appObservers = new HashSet<>();

    public TcpClient(State.Dispatcher fg, ExecutorService bg, LogLevel.Logger log, Socket socket)
            throws IOException {
        this.fg = fg;
        this.log = log;
        this.socket = socket;
        input = socket.getInputStream();
        output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        watchResponse = bg.submit(this);
    }

    @Override
    public boolean supports(int feature) {
        return (feature & (FEATURE_POLLING | FEATURE_NOTIFICATIONS)) != 0;
    }

    @Override
    public Link observePlayer(final OnPlayerNotification observer) {
        playerObservers.add(observer);
        return new Link() {
            @Override
            public void unlink() {
                playerObservers.remove(observer);
            }
        };
    }

    @Override
    public Link observeSystem(final OnSystemNotification observer) {
        systemObservers.add(observer);
        return new Link() {
            @Override
            public void unlink() {
                systemObservers.remove(observer);
            }
        };
    }

    @Override
    public Link observeInput(final OnInputNotification observer) {
        inputObservers.add(observer);
        return new Link() {
            @Override
            public void unlink() {
                inputObservers.remove(observer);
            }
        };
    }

    @Override
    public Link observeApplication(final OnApplicationNotification observer) {
        appObservers.add(observer);
        return new Link() {
            @Override
            public void unlink() {
                appObservers.remove(observer);
            }
        };
    }

    @Override
    public <T> T send(ApiMethod<T> apiRequest) throws InterruptedException, ApiException {
        int id = apiRequest.getId();
        Result<String> either = new Result<>();
        synchronized (pending) {
            if (pending.containsKey(id)) {
                throw new ApiException(ApiException.API_METHOD_WITH_SAME_ID_ALREADY_EXECUTING,
                        "Request already sent");
            }
            pending.put(id, either);
        }
        try {
            String json = apiRequest.toJsonString();
            output.write(json);
            output.flush();
            LogLevel.D.to(log, "Sent via TCP: %s", json);
        } catch (IOException e) {
            throw new ApiException(ApiException.IO_EXCEPTION_WHILE_SENDING_REQUEST, e);
        }
        if (!either.await()) {
            throw (InterruptedException) either.error;
        }
        return apiRequest.resultFromJson(JsonResponse.verify(either.result));
    }

    @Override
    public void dispose() {
        playerObservers.clear();
        systemObservers.clear();
        inputObservers.clear();
        appObservers.clear();
        watchResponse.cancel(true);
        for (Closeable e : new Closeable[]{input, output, socket}) try {
            e.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        JsonParser parser = null;
        try {
            parser = JsonResponse.MAPPER.getFactory().createParser(input);
            ObjectNode message;
            while (null != (message = JsonResponse.MAPPER.readTree(parser))) {
                if (message.has(ApiMethod.ID_NODE)) {
                    int id = message.get(ApiMethod.ID_NODE).asInt();
                    LogLevel.D.to(log, "Got response for request: %d", id);
                    Result<String> either;
                    synchronized (pending) {
                        either = pending.remove(id);
                    }
                    if (either == null) {
                        LogLevel.E.to(log, "Possible duplicate response to id: %d", id);
                        continue;
                    }
                    either.ok(message.toString());
                } else if (message.has(ApiMethod.METHOD_NODE)) {
                    final String type = message.get(ApiMethod.METHOD_NODE).asText();
                    final ObjectNode params = (ObjectNode) message.get(ApiMethod.PARAMS_NODE);
                    LogLevel.D.to(log, "Got notification: %s(%s)", type, params);
                    fg.run(new Runnable() {
                        @Override
                        public void run() {
                            sendNotification(type, params);
                        }
                    });
                }
            }
        } catch (IOException e) {
            LogLevel.E.to(log, e, "Error while reading response");
            if (!Thread.interrupted()) {
                fg.handle(e);
            }
        } finally {
            if (parser != null) try {
                parser.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendNotification(String type, ObjectNode params) {
        switch (type) {
            case Player.OnPause.NOTIFICATION_NAME:
                Player.OnPause onPause = new Player.OnPause(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerPaused(onPause);
                }
                break;
            case Player.OnPlay.NOTIFICATION_NAME:
                Player.OnPlay onPlay = new Player.OnPlay(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerPlayed(onPlay);
                }
                break;
            case Player.OnPropertyChanged.NOTIFICATION_NAME:
                Player.OnPropertyChanged onPropertyChanged = new Player.OnPropertyChanged(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerPropertyChanged(onPropertyChanged);
                }
                break;
            case Player.OnSeek.NOTIFICATION_NAME:
                Player.OnSeek onSeek = new Player.OnSeek(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerSought(onSeek);
                }
                break;
            case Player.OnSpeedChanged.NOTIFICATION_NAME:
                Player.OnSpeedChanged onSpeedChanged = new Player.OnSpeedChanged(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerSpeedChanged(onSpeedChanged);
                }
                break;
            case Player.OnStop.NOTIFICATION_NAME:
                Player.OnStop onStop = new Player.OnStop(params);
                for (OnPlayerNotification on : playerObservers) {
                    on.playerStopped(onStop);
                }
                break;
            case System.OnQuit.NOTIFICATION_NAME:
                System.OnQuit onQuit = new System.OnQuit(params);
                for (OnSystemNotification on : systemObservers) {
                    on.systemQuit(onQuit);
                }
                break;
            case System.OnRestart.NOTIFICATION_NAME:
                System.OnRestart onRestart = new System.OnRestart(params);
                for (OnSystemNotification on : systemObservers) {
                    on.systemRestarted(onRestart);
                }
                break;
            case System.OnSleep.NOTIFICATION_NAME:
                System.OnSleep onSleep = new System.OnSleep(params);
                for (OnSystemNotification on : systemObservers) {
                    on.systemSlept(onSleep);
                }
                break;
            case Input.OnInputRequested.NOTIFICATION_NAME:
                Input.OnInputRequested onInputRequested = new Input.OnInputRequested(params);
                for (OnInputNotification on : inputObservers) {
                    on.inputRequested(onInputRequested);
                }
                break;
            case Application.OnVolumeChanged.NOTIFICATION_NAME:
                Application.OnVolumeChanged onVolumeChanged =
                        new Application.OnVolumeChanged(params);
                for (OnApplicationNotification on : appObservers) {
                    on.appVolumeChanged(onVolumeChanged);
                }
                break;
            default:
                LogLevel.E.to(log, "Unhandled notification: %s", type);
                break;
        }
    }

    public static class Builder {
        private final InetSocketAddress address;
        private State.Dispatcher fg = Globals.IMMEDIATE;
        private ExecutorService bg = Globals.IO;
        private LogLevel.Logger log;

        public Builder(String host, int port) {
            address = new InetSocketAddress(host, port);
        }

        public Builder(HostInfo hostInfo) {
            this(hostInfo.getAddress(), hostInfo.getTcpPort());
        }

        public Builder withDispatcher(State.Dispatcher fg) {
            if (fg != null) {
                this.fg = fg;
            }
            return this;
        }

        public Builder withBgExecutor(ExecutorService bg) {
            if (bg != null) {
                this.bg = bg;
            }
            return this;
        }

        public Builder withLogger(LogLevel.Logger log) {
            this.log = log;
            return this;
        }

        public TcpClient build() throws ApiException {
            return build(5_000);
        }

        public TcpClient build(int timeout) throws ApiException {
            Socket socket = new Socket();
            try {
                socket.setSoTimeout(30_000);
                socket.connect(address, timeout);
                return new TcpClient(fg, bg, log, socket);
            } catch (IOException e) {
                throw new ApiException(ApiException.IO_EXCEPTION_WHILE_CONNECTING, e);
            }
        }
    }

}
