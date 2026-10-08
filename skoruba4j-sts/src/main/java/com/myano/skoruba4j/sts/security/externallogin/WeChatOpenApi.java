package com.myano.skoruba4j.sts.security.externallogin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** WeChat Open Platform website-app OAuth (qrconnect / sns). */
@Component
public final class WeChatOpenApi {
  private static final Logger log = LoggerFactory.getLogger(WeChatOpenApi.class);

  public record TokenResult(String accessToken, String openId, String unionId) {}

  public record Profile(String nickname) {}

  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public WeChatOpenApi(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public static String authorizeUrl(String appId, String redirectUri, String state) {
    return "https://open.weixin.qq.com/connect/qrconnect?appid="
        + enc(appId)
        + "&redirect_uri="
        + enc(redirectUri)
        + "&response_type=code&scope=snsapi_login&state="
        + enc(state)
        + "#wechat_redirect";
  }

  public Optional<TokenResult> exchangeCode(String appId, String appSecret, String code) {
    if (blank(appId) || blank(appSecret) || blank(code)) {
      return Optional.empty();
    }
    String url =
        "https://api.weixin.qq.com/sns/oauth2/access_token?appid="
            + enc(appId)
            + "&secret="
            + enc(appSecret)
            + "&code="
            + enc(code)
            + "&grant_type=authorization_code";
    try {
      JsonNode json = getJson(url);
      if (json == null || json.has("errcode") && json.path("errcode").asInt(0) != 0) {
        log.warn(
            "WeChat token exchange failed: {}",
            json == null ? "null" : json.path("errmsg").asText("error"));
        return Optional.empty();
      }
      String openId = json.path("openid").asText("");
      if (openId.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(
          new TokenResult(
              json.path("access_token").asText(""),
              openId,
              json.path("unionid").asText("")));
    } catch (Exception e) {
      log.warn("WeChat token exchange error: {}", e.getMessage());
      return Optional.empty();
    }
  }

  public Optional<Profile> userInfo(String accessToken, String openId) {
    if (blank(accessToken) || blank(openId)) {
      return Optional.empty();
    }
    String url =
        "https://api.weixin.qq.com/sns/userinfo?access_token="
            + enc(accessToken)
            + "&openid="
            + enc(openId);
    try {
      JsonNode json = getJson(url);
      if (json == null || json.has("errcode") && json.path("errcode").asInt(0) != 0) {
        return Optional.empty();
      }
      String nickname = json.path("nickname").asText("");
      return Optional.of(new Profile(nickname));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private JsonNode getJson(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .header("Accept", "application/json")
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return null;
    }
    return mapper.readTree(response.body());
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
