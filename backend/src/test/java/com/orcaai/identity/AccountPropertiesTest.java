package com.orcaai.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AccountPropertiesTest {

    @Test
    void acceptsHttpsAndLocalHttp() {
        assertThat(properties("https://app.orcaai.com.br").link("/verify-email", "abc"))
                .isEqualTo("https://app.orcaai.com.br/verify-email#token=abc");
        assertThat(properties("http://localhost:3000/").link("/reset-password", "abc"))
                .isEqualTo("http://localhost:3000/reset-password#token=abc");
    }

    @Test
    void rejectsUrlsThatCouldLeakOrRedirectTokens() {
        for (String url : new String[] {
                "http://app.orcaai.com.br", "app.orcaai.com.br", "https://app.orcaai.com.br?next=x",
                "https://app.orcaai.com.br#x", "https://user:pass@app.orcaai.com.br", "ftp://app.orcaai.com.br"}) {
            assertThatThrownBy(() -> properties(url)).as(url).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static AccountProperties properties(String publicUrl) {
        return new AccountProperties(URI.create(publicUrl), "nao-responda@orcaai.com.br",
                Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(72), Duration.ofMinutes(2));
    }
}
