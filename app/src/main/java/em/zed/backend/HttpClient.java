package em.zed.backend;

/*
 * This file is a part of the Kore project.
 */

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.jsonrpc.ApiException;
import org.xbmc.kore.jsonrpc.ApiMethod;

import java.io.IOException;
import java.net.Proxy;

import em.zed.util.LogLevel;

public class HttpClient implements ApiClient {

    private static final MediaType TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final LogLevel.Logger log;
    private final String hostUrl;

    public HttpClient(OkHttpClient httpClient, LogLevel.Logger log, String hostUrl) {
        this.httpClient = httpClient;
        this.log = log;
        this.hostUrl = hostUrl;
    }

    @Override
    public boolean supports(int feature) {
        return (feature & FEATURE_POLLING) != 0;
    }

    @Override
    public Link observePlayer(OnPlayerNotification observer) {
        LogLevel.I.to(log, "Notifications not supported via Http");
        return NOOP;
    }

    @Override
    public Link observeSystem(OnSystemNotification observer) {
        LogLevel.I.to(log, "Notifications not supported via Http");
        return NOOP;
    }

    @Override
    public Link observeInput(OnInputNotification observer) {
        LogLevel.I.to(log, "Notifications not supported via Http");
        return NOOP;
    }

    @Override
    public Link observeApplication(OnApplicationNotification observer) {
        LogLevel.I.to(log, "Notifications not supported via Http");
        return NOOP;
    }

    @Override
    public <T> T send(ApiMethod<T> apiRequest) throws ApiException, InterruptedException {
        LogLevel.D.to(log, "Sending API request: %s to: %s", apiRequest.getMethodName(), hostUrl);
        Request request = new Request.Builder()
                .url(hostUrl)
                .post(RequestBody.create(TYPE_JSON, apiRequest.toJsonString()))
                .build();
        final Result<Response> either = new Result<>();
        Call call = httpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                either.err(e);
            }

            @Override
            public void onResponse(Response response) {
                either.ok(response);
            }
        });
        if (!either.await()) {
            call.cancel();
            LogLevel.I.to(log, "Api request cancelled");
            throw (InterruptedException) either.error;
        }
        if (either.error != null) {
            throw new ApiException(ApiException.IO_EXCEPTION_WHILE_SENDING_REQUEST, either.error);
        }
        ResponseBody body = either.result.body();
        int code = either.result.code();
        switch (code) {
            case 200:
                try {
                    String json = body.string();
                    LogLevel.D.to(log, "Got response: %s", json);
                    return apiRequest.resultFromJson(JsonResponse.verify(json));
                } catch (IOException e) {
                    throw new ApiException(ApiException.IO_EXCEPTION_WHILE_READING_RESPONSE, e);
                } finally {
                    try {
                        body.close();
                    } catch (IOException ignored) {
                    }
                }
            case 204:  // no content
                return null;
            case 401:  // fallthrough
            case 403:
                throw new ApiException(ApiException.HTTP_RESPONSE_CODE_UNAUTHORIZED,
                        "Response code: " + code);
            case 404:
                throw new ApiException(ApiException.HTTP_RESPONSE_CODE_NOT_FOUND,
                        "Response code: " + code);
            default:
                throw new ApiException(ApiException.HTTP_RESPONSE_CODE_UNKNOWN,
                        "Response code: " + code);
        }
    }

    @Override
    public void dispose() {
    }

    public static class Builder {
        private final OkHttpClient client = new OkHttpClient();
        private final String hostUrl;
        private LogLevel.Logger log;

        public Builder(String hostUrl) {
            this.hostUrl = hostUrl;
        }

        public Builder(HostInfo hostInfo) {
            this(hostInfo.getJsonRpcHttpEndpoint());
            withCredentials(hostInfo.getUsername(), hostInfo.getPassword());
        }

        public Builder withCredentials(final String username, final String password) {
            if (username != null && !username.isEmpty()) {
                client.setAuthenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Proxy proxy, Response response) {
                        return response.request()
                                .newBuilder()
                                .header("Authorization", Credentials.basic(username, password))
                                .build();
                    }

                    @Override
                    public Request authenticateProxy(Proxy proxy, Response response) {
                        return null;
                    }
                });
            }
            return this;
        }

        public Builder withLogger(LogLevel.Logger log) {
            this.log = log;
            return this;
        }

        public HttpClient build() {
            return new HttpClient(client, log, hostUrl);
        }
    }

}
