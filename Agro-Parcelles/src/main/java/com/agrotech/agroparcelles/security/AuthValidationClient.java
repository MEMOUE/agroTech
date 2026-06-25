package com.agrotech.agroparcelles.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AuthValidationClient {

    private final RestClient restClient;

    public AuthValidationClient(
            @Value("${services.auth.url:http://localhost:8081}") String authUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .build();
    }

    public TokenValidationResponse validate(String token) {
        try {
            return restClient.get()
                    .uri("/api/auth/validate")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(TokenValidationResponse.class);
        } catch (RestClientException e) {
            log.warn("Erreur validation token auprès de Agro-Auth : {}", e.getMessage());
            return null;
        }
    }
}
