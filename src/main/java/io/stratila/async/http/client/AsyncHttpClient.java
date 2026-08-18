package io.stratila.async.http.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class AsyncHttpClient {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static OAuth2TokenManager tokenManager;

  public static void setTokenManager(OAuth2TokenManager tokenManager) {
    AsyncHttpClient.tokenManager = tokenManager;
  }

  public static CompletableFuture<String> get(String url) {
    if (tokenManager == null) {
      return CompletableFuture.failedFuture(new RuntimeException("Token manager not set"));
    }

    return tokenManager
        .getAccessToken()
        .thenCompose(
            token -> {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(URI.create(url))
                      .version(HttpClient.Version.HTTP_1_1)
                      .header("Authorization", "Bearer " + token)
                      .GET()
                      .build();

              return CLIENT
                  .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenApply(HttpResponse::body);
            });
  }

  public static CompletableFuture<String> postJson(String url, String json) {
    if (tokenManager == null) {
      return CompletableFuture.failedFuture(new RuntimeException("Token manager not set"));
    }

    return tokenManager
        .getAccessToken()
        .thenCompose(
            token -> {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(URI.create(url))
                      .version(HttpClient.Version.HTTP_1_1)
                      .header("Authorization", "Bearer " + token)
                      .header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString(json))
                      .build();
              return CLIENT
                  .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenApply(HttpResponse::body);
            });
  }
}
