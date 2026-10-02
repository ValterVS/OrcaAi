package com.orcaai.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.shared.security.Role;
import com.orcaai.support.TestData;
import com.orcaai.support.TestcontainersConfiguration;
import com.orcaai.users.User;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cookie attributes are checked against a real server: under MockMvc, Spring Session derives them
 * from the mock servlet context instead of the application configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionCookieIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    TestData testData;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void sessionCookieIsHttpOnlySecureAndSameSite() throws Exception {
        User user = testData.user(testData.organization(), PASSWORD, Role.MEMBER);

        HttpResponse<Void> csrf = http.send(
                HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.discarding());
        String csrfCookie = setCookie(csrf.headers().allValues("Set-Cookie"), "XSRF-TOKEN");
        String csrfToken = csrfCookie.substring("XSRF-TOKEN=".length(), csrfCookie.indexOf(';'));

        String form = "email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<Void> login = http.send(
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(setCookie(login.headers().allValues("Set-Cookie"), "SESSION"))
                .contains("HttpOnly", "Secure", "SameSite=Lax");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String setCookie(List<String> headers, String name) {
        return headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + name + " in " + headers));
    }
}
