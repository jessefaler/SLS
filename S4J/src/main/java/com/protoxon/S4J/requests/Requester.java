

package com.protoxon.S4J.requests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;
import javax.net.ssl.SSLPeerUnverifiedException;

import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.exceptions.HttpException;
import com.protoxon.S4J.exceptions.LoginException;
import com.protoxon.S4J.utils.S4JLogger;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;

public class Requester {

	private final S4J api;
	private final Logger REQUESTER_LOG = S4JLogger.getLogger(Requester.class);

	public static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);

	public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf8");
	public static final MediaType MEDIA_TYPE_PLAIN = MediaType.parse("text/plain; charset=utf8");
	public static final MediaType MEDIA_TYPE_OCTET = MediaType.parse("application/octet-stream; charset=utf-8");

	private static final String SLS_API_PREFIX = "%s/api/";

	private final RateLimiter rateLimiter;
	private final OkHttpClient client;
    private final OkHttpClient SSEClient;

	public Requester(S4J api) {
		this.api = api;
		this.rateLimiter = new RateLimiter(this, api);
		this.client = api.getHttpClient();

        // SSE CLIENT
        this.SSEClient = api.getHttpClient().newBuilder()
                .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
	}

	public <T> void request(Request<T> request) {
		if (request.shouldQueue()) rateLimiter.queueRequest(request);
		else execute(request, true);
	}

	public Long execute(Request<?> apiRequest) {
		return execute(apiRequest, false);
	}

	public Long execute(Request<?> request, boolean handleOnRateLimit) {
		return execute(request, false, handleOnRateLimit);
	}

	public Long execute(Request<?> apiRequest, boolean retried, boolean handleOnRateLimit) {

		Route.CompiledRoute route = apiRequest.getRoute();
		Long retryAfter = rateLimiter.getRateLimit();

		if (retryAfter > 0) {
			if (handleOnRateLimit) apiRequest.handleResponse(new Response(retryAfter));
			return retryAfter;
		}

		okhttp3.Request.Builder builder = new okhttp3.Request.Builder();

		if (api.getUrl() == null || api.getUrl().isEmpty())
			throw new HttpException("No SLS api URL was defined.");
		String SLSUrl = api.getUrl();
		if (SLSUrl.endsWith("/")) SLSUrl = SLSUrl.substring(0, SLSUrl.length() - 1);
		String url = String.format(SLS_API_PREFIX, SLSUrl)
				+ apiRequest.getRoute().getCompiledRoute();

		builder.url(url);
		String method = route.getMethod().toString();
		if (apiRequest.getRequestBody() != null) builder.method(method, apiRequest.getRequestBody());
		else if (HttpMethod.requiresRequestBody(method)) builder.method(method, EMPTY_BODY);
		else builder.method(method, null);

		builder.header("Accept", "application/vnd.sls.v1+json");

		if (api.getToken() == null || api.getToken().isEmpty())
			throw new LoginException("No authorization token was defined.");
		builder.header("Authorization", "Bearer " + api.getToken());

		okhttp3.Request request = builder.build();

		okhttp3.Response[] responses = new okhttp3.Response[4];
		okhttp3.Response lastResponse = null;

		try {
			REQUESTER_LOG.debug("Executing request {} {}", route.getMethod(), route.getCompiledRoute());
			int attempt = 0;
			do {
				if (apiRequest.isSkipped()) return null;

				Call call = client.newCall(request);
				lastResponse = call.execute();
				responses[attempt] = lastResponse;

				// Don't retry on non-5XX responses or 502 Bad Gateway and 503 Service Unavailable
				// 502 means the downstream node could not be reached and 503 means the node is unavailable
				if (lastResponse.code() < 500 || lastResponse.code() == 502) break;

				attempt++;
				REQUESTER_LOG.debug(
						"Requesting {} -> {} returned status {}... retrying (attempt {})",
						route.getMethod(),
						route.getCompiledRoute(),
						lastResponse.code(),
						attempt);
				try {
					Thread.sleep(50L * attempt);
				} catch (InterruptedException ignored) {
				}
			} while (attempt < 3 && lastResponse.code() >= 500);

			REQUESTER_LOG.trace(
					"Finished Request {} {} with code {}",
					route.getMethod(),
					lastResponse.request().url(),
					lastResponse.code());

			if (lastResponse.code() >= 500) {
				// epic fucking fail
				Response response = new Response(lastResponse, -1);
				apiRequest.handleResponse(response);
				return null;
			}

			retryAfter = rateLimiter.handleResponse(apiRequest, lastResponse);

			if (retryAfter == null) apiRequest.handleResponse(new Response(lastResponse, -1));
			else if (handleOnRateLimit) apiRequest.handleResponse(new Response(lastResponse, retryAfter));

			return retryAfter;
		} catch (SocketTimeoutException e) {
			if (!retried) return execute(apiRequest, true, handleOnRateLimit);
			REQUESTER_LOG.error("Requester timed out while executing a request {}", e.getMessage());
			apiRequest.handleResponse(new Response(lastResponse, e));
			return null;
		} catch (Exception e) {
			if (!retried && isRetry(e)) return execute(apiRequest, true, handleOnRateLimit);
			if (e.getMessage() == null) REQUESTER_LOG.error("There was an exception while executing a request");
			else REQUESTER_LOG.error("{}", e.getMessage());
			apiRequest.handleResponse(new Response(lastResponse, e));
			return null;
		} finally {
			for (okhttp3.Response r : responses) {
				if (r == null) break;
				r.close();
			}
		}
	}

	private static boolean isRetry(Throwable e) {
		return e instanceof SocketException // Socket couldn't be created or access failed
				|| e instanceof SocketTimeoutException // Connection timed out
				|| e instanceof SSLPeerUnverifiedException; // SSL Certificate was wrong
	}

    public Call stream(Route.CompiledRoute route, Consumer<String> onEvent, Consumer<Throwable> onError) {

        String SLSUrl = api.getUrl();
        if (SLSUrl.endsWith("/"))
            SLSUrl = SLSUrl.substring(0, SLSUrl.length() - 1);

        String url = String.format(SLS_API_PREFIX, SLSUrl) + route.getCompiledRoute();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + api.getToken())
                .get()
                .build();

        Call call = SSEClient.newCall(request);

        call.enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(response.body().byteStream()))) {

                    StringBuilder buffer = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            onEvent.accept(buffer.toString());
                            buffer.setLength(0);
                        } else {
                            buffer.append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });

        return call;
    }

    public WebSocket websocket(Route.CompiledRoute route, Consumer<String> onMessage, Consumer<Throwable> onError) {
        String SLSUrl = api.getUrl();
        if (SLSUrl.endsWith("/"))
            SLSUrl = SLSUrl.substring(0, SLSUrl.length() - 1);

        String httpUrl = String.format(SLS_API_PREFIX, SLSUrl) + route.getCompiledRoute();
        
        // Convert HTTP URL to WebSocket URL
        String wsUrl = httpUrl.replace("http://", "ws://").replace("https://", "wss://");

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + api.getToken())
                .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                REQUESTER_LOG.debug("WebSocket connection opened to {}", wsUrl);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    onMessage.accept(text);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                try {
                    onMessage.accept(bytes.utf8());
                } catch (Exception e) {
                    onError.accept(e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                REQUESTER_LOG.debug("WebSocket connection closed: {} {}", code, reason);
                if (code != 1000) {
                    onError.accept(new IOException("WebSocket closed unexpectedly: " + code + " " + reason));
                } else {
                    onError.accept(new IOException("WebSocket closed: " + code + " " + reason));
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                // Error logging is handled by the caller (e.g., WSAction) to avoid duplicate logs
                onError.accept(t);
            }
        };

        return client.newWebSocket(request, listener);
    }

}
