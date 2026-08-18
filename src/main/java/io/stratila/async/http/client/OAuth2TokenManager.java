package io.stratila.async.http.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class OAuth2TokenManager {
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private final String authServerUrl;
  private final String clientId;
  private final String clientSecret;

  private String cachedToken;
  private long tokenExpiresAt;

  public OAuth2TokenManager(String authServerUrl, String clientId, String clientSecret) {
    this.authServerUrl = authServerUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenExpiresAt = 0;
  }

  public CompletableFuture<String> getAccessToken() {
    if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 30000) {
      return CompletableFuture.completedFuture(cachedToken);
    } else {
      return fetchNewToken();
    }
  }

  private CompletableFuture<String> fetchNewToken() {
    String basicAuth =
        Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(authServerUrl))
            .header("Authorization", "Basic " + basicAuth)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
            .build();

    return HTTP_CLIENT
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get token: " + response.body());
              }
              JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
              cachedToken = jsonObject.get("access_token").getAsString();
              int expiresIn = jsonObject.get("expires_in").getAsInt();
              tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
              return cachedToken;
            });
  }
}
