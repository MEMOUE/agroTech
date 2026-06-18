package com.agrotech.agroauth.controller;

import com.agrotech.agroauth.dto.*;
import com.agrotech.agroauth.entity.Role;
import com.agrotech.agroauth.exception.GlobalExceptionHandler;
import com.agrotech.agroauth.exception.UserAlreadyExistsException;
import com.agrotech.agroauth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .token("jwt_token")
                .username("john")
                .name("John Doe")
                .email("john@example.com")
                .role(Role.AGRICULTEUR)
                .build();
    }

    // ---- POST /register ----

    @Test
    void register_returns201OnSuccess() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setName("John Doe");
        req.setEmail("john@example.com");
        req.setPassword("Password1!");

        when(authService.register(any())).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt_token"))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("AGRICULTEUR"));
    }

    @Test
    void register_returns400WhenUsernameBlank() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("");
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void register_returns400WhenEmailInvalid() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setName("John");
        req.setEmail("not-an-email");
        req.setPassword("Password1!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void register_returns400WhenPasswordTooWeak() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("weak");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_returns409WhenUserAlreadyExists() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("john");
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1!");

        when(authService.register(any())).thenThrow(new UserAlreadyExistsException("Nom d'utilisateur déjà pris"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    // ---- POST /login ----

    @Test
    void login_returns200OnSuccess() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("john");
        req.setPassword("Password1!");

        when(authService.login(any())).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_token"))
                .andExpect(jsonPath("$.username").value("john"));
    }

    @Test
    void login_returns400WhenFieldsMissing() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // ---- GET /validate ----

    @Test
    void validate_returns200ForValidToken() throws Exception {
        TokenValidationResponse resp = TokenValidationResponse.builder()
                .valid(true).username("john").role(Role.AGRICULTEUR).build();

        when(authService.validateToken("valid_token")).thenReturn(resp);

        mockMvc.perform(get("/api/auth/validate")
                        .header("Authorization", "Bearer valid_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("john"));
    }

    @Test
    void validate_returns401WhenHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/auth/validate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validate_returns401ForInvalidToken() throws Exception {
        TokenValidationResponse resp = TokenValidationResponse.builder()
                .valid(false).build();

        when(authService.validateToken("bad_token")).thenReturn(resp);

        mockMvc.perform(get("/api/auth/validate")
                        .header("Authorization", "Bearer bad_token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    // ---- GET /me ----

    @Test
    void getProfile_returns200WithUserDetails() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(1L).username("john").name("John Doe")
                .email("john@example.com").role(Role.AGRICULTEUR)
                .enabled(true).createdAt(LocalDateTime.now())
                .build();

        when(authService.getProfile(any())).thenReturn(profile);

        mockMvc.perform(get("/api/auth/me")
                        .principal(() -> "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("AGRICULTEUR"));
    }

    @Test
    void getProfile_returns401WhenUserNotFound() throws Exception {
        when(authService.getProfile(any())).thenThrow(new UsernameNotFoundException("not found"));

        mockMvc.perform(get("/api/auth/me")
                        .principal(() -> "unknown"))
                .andExpect(status().isUnauthorized());
    }
}
