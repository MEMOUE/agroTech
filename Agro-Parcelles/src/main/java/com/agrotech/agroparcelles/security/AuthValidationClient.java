package com.agrotech.agroparcelles.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AuthValidationClient {

    private final RestClient restClient;

    public AuthValidationClient(
            @Value("${services.auth.url:http://localhost:8081}") String authUrl) {
        // Fabrique statique : indépendante de l'auto-config.
        // En SB4, le bean RestClient.Builder n'est plus fourni par le starter webmvc.
        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .build();
    }

    public TokenValidationResponse validate(String token) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/auth/validate")
                            .queryParam("token", token)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .body(TokenValidationResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }
}