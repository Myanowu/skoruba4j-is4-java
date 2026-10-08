package com.myano.skoruba4j.sts.security.externallogin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeChatOpenApiTest {

  @Test
  void authorizeUrlContainsQrconnectAndState() {
    String url =
        WeChatOpenApi.authorizeUrl(
            "wxAPP", "https://sts.example/external/wechat/callback", "abc123");
    assertTrue(url.startsWith("https://open.weixin.qq.com/connect/qrconnect?"));
    assertTrue(url.contains("appid=wxAPP"));
    assertTrue(url.contains("scope=snsapi_login"));
    assertTrue(url.contains("state=abc123"));
    assertTrue(url.endsWith("#wechat_redirect"));
    assertTrue(url.contains("redirect_uri="));
  }
}
