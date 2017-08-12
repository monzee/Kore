package em.zed.backend;

/*
 * This file is a part of the Kore project.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.xbmc.kore.jsonrpc.ApiException;
import org.xbmc.kore.jsonrpc.ApiMethod;
import org.xbmc.kore.jsonrpc.notification.Application;
import org.xbmc.kore.jsonrpc.notification.Input;
import org.xbmc.kore.jsonrpc.notification.Player;
import org.xbmc.kore.jsonrpc.notification.System;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public interface ApiClient {

    interface OnPlayerNotification {
        void playerPropertyChanged(Player.OnPropertyChanged notification);
        void playerPlayed(Player.OnPlay notification);
        void playerPaused(Player.OnPause notification);
        void playerSpeedChanged(Player.OnSpeedChanged notification);
        void playerSought(Player.OnSeek notification);
        void playerStopped(Player.OnStop notification);
    }

    interface OnSystemNotification {
        void systemQuit(System.OnQuit notification);
        void systemRestarted(System.OnRestart notification);
        void systemSlept(System.OnSleep notification);
    }

    interface OnInputNotification {
        void inputRequested(Input.OnInputRequested notification);
    }

    interface OnApplicationNotification {
        void appVolumeChanged(Application.OnVolumeChanged notification);
    }

    interface Link {
        void unlink();
    }

    final class Result<T> {
        final CountDownLatch done = new CountDownLatch(1);
        Exception error;
        T result;

        void ok(T response) {
            this.result = response;
            done.countDown();
        }

        void err(Exception e) {
            error = e;
            done.countDown();
        }

        boolean await() {
            try {
                done.await();
                return true;
            } catch (InterruptedException e) {
                error = e;
                return false;
            }
        }
    }

    final class JsonResponse {
        static final ObjectMapper MAPPER = new ObjectMapper();

        static ObjectNode verify(String json) throws ApiException {
            ObjectNode node;
            try {
                node = (ObjectNode) MAPPER.readTree(json);
            } catch (IOException e) {
                throw new ApiException(ApiException.IO_EXCEPTION_WHILE_READING_RESPONSE, e);
            }
            if (node.has(ApiMethod.ERROR_NODE)) {
                throw new ApiException(ApiException.API_ERROR, node);
            }
            if (!node.has(ApiMethod.RESULT_NODE)) {
                throw new ApiException(ApiException.INVALID_JSON_RESPONSE_FROM_HOST, node);
            }
            return node;
        }

        private JsonResponse() {}
    }

    int FEATURE_POLLING = 1;
    int FEATURE_NOTIFICATIONS = 2;

    Link NOOP = new Link() {
        @Override
        public void unlink() {
            // no-op
        }
    };

    ApiClient DISCONNECTED = new ApiClient() {
        @Override
        public boolean supports(int feature) {
            return false;
        }

        @Override
        public Link observePlayer(OnPlayerNotification observer) {
            return NOOP;
        }

        @Override
        public Link observeSystem(OnSystemNotification observer) {
            return NOOP;
        }

        @Override
        public Link observeInput(OnInputNotification observer) {
            return NOOP;
        }

        @Override
        public Link observeApplication(OnApplicationNotification observer) {
            return NOOP;
        }

        @Override
        public <T> T send(ApiMethod<T> apiRequest) throws ApiException {
            throw new ApiException(ApiException.API_NO_CONNECTION, "Not connected.");
        }

        @Override
        public void dispose() {}
    };

    boolean supports(int feature);
    Link observePlayer(OnPlayerNotification observer);
    Link observeSystem(OnSystemNotification observer);
    Link observeInput(OnInputNotification observer);
    Link observeApplication(OnApplicationNotification observer);
    <T> T send(ApiMethod<T> apiRequest) throws InterruptedException, ApiException;
    void dispose();
}
