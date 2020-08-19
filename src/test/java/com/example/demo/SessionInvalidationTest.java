package com.example.demo;

import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionInvalidationTest {

    @LocalServerPort
    int port;

    @Test
    void testSessionInInvalidatedOnLogout() throws Exception {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("user", "password"));

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build()) {

            // create a context and use it for each request to have persistent observable cookies store
            HttpClientContext context = HttpClientContext.create();

            HttpGet httpget = new HttpGet("http://localhost:" + port);

            // on the first request a new session is created, the response indicates there is no value in session yet
            try (CloseableHttpResponse response = httpClient.execute(httpget, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
                assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("empty");
            }

            // just observe the session id to ensure logout re-issues a new session (sanity check for test logic)
            String initialSessionId = getSessionCookie(context.getCookieStore());
            assertThat(initialSessionId).isNotNull();

            // on the second request existing session is used, the response indicates there is a value retrieved from the session
            try (CloseableHttpResponse response = httpClient.execute(httpget, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
                assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("value");
            }

            // just observe the session id to ensure we manage the cookies correctly
            String secondRequestSessionId = getSessionCookie(context.getCookieStore());
            assertThat(secondRequestSessionId).isEqualTo(initialSessionId);

            // execute logout
            HttpPost httpPost = new HttpPost("http://localhost:" + port + "/logout");
            try (CloseableHttpResponse response = httpClient.execute(httpPost, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(302);
                assertThat(response.getLastHeader(HttpHeaders.LOCATION)).isNotNull().satisfies(loc ->
                        assertThat(loc.getValue()).isEqualTo("/login?logout"));
            }

            // ensure the new session cookie is issued, i.e. the new session is started
            String sessionAfterLogout = getSessionCookie(context.getCookieStore());
            assertThat(sessionAfterLogout).isNotNull().isNotEqualTo(initialSessionId);

            // expect no existing value in the new session; fails as session is not invalidated
            try (CloseableHttpResponse response = httpClient.execute(httpget, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
                assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("empty");
            }
        }
    }

    private String getSessionCookie(CookieStore cookieStore) {
        return cookieStore.getCookies()
                .stream()
                .filter(cookie -> cookie.getName().equals("SESSION"))
                .map(Cookie::getValue)
                .findAny()
                .orElse(null);
    }
}
