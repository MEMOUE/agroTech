package com.agrotech.agroauth.service;

import com.agrotech.agroauth.dto.*;
import com.agrotech.agroauth.entity.Role;
import com.agrotech.agroauth.entity.User;
import com.agrotech.agroauth.exception.UserAlreadyExistsException;
import com.agrotech.agroauth.repository.UserRepository;
import com.agrotech.agroauth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User user;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("john")
                .name("John Doe")
                .email("john@example.com")
                .password("hashed_password")
                .role(Role.AGRICULTEUR)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setUsername("john");
        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPassword("Password1!");
        registerRequest.setRole(Role.AGRICULTEUR);

        loginRequest = new LoginRequest();
        loginRequest.setUsername("john");
        loginRequest.setPassword("Password1!");
    }

    // ---- register ----

    @Test
    void register_success() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtUtil.generateToken("john", "AGRICULTEUR")).thenReturn("jwt_token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("jwt_token");
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getRole()).isEqualTo(Role.AGRICULTEUR);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_defaultsToAGRICULTEUR_whenRoleIsNull() {
        registerRequest.setRole(null);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtUtil.generateToken(any(), any())).thenReturn("token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getRole()).isEqualTo(Role.AGRICULTEUR);
    }

    @Test
    void register_throwsWhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("nom d'utilisateur");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_normalizesEmailToLowercase() {
        registerRequest.setEmail("JOHN@EXAMPLE.COM");
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail("JOHN@EXAMPLE.COM")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        User savedUser = User.builder()
                .username("john").name("John Doe")
                .email("john@example.com").password("hashed")
                .role(Role.AGRICULTEUR).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken(any(), any())).thenReturn("token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getEmail()).isEqualTo("john@example.com");
    }

    // ---- login ----

    @Test
    void login_success() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("john", "AGRICULTEUR")).thenReturn("jwt_token");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getToken()).isEqualTo("jwt_token");
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void login_throwsOnBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void login_throwsWhenUserNotFound() {
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("john", "Password1!"));
        when(userRepository.findByUsername("john")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // ---- validateToken ----

    @Test
    void validateToken_returnsValidResponse() {
        when(jwtUtil.validateToken("valid_token")).thenReturn(true);
        when(jwtUtil.extractUsername("valid_token")).thenReturn("john");
        when(jwtUtil.extractRole("valid_token")).thenReturn("AGRICULTEUR");

        TokenValidationResponse response = authService.validateToken("valid_token");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getRole()).isEqualTo(Role.AGRICULTEUR);
    }

    @Test
    void validateToken_returnsInvalidResponseForBadToken() {
        when(jwtUtil.validateToken("bad_token")).thenReturn(false);

        TokenValidationResponse response = authService.validateToken("bad_token");

        assertThat(response.isValid()).isFalse();
        assertThat(response.getUsername()).isNull();
    }

    // ---- getProfile ----

    @Test
    void getProfile_returnsUserProfile() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile("john");

        assertThat(profile.getUsername()).isEqualTo("john");
        assertThat(profile.getEmail()).isEqualTo("john@example.com");
        assertThat(profile.getRole()).isEqualTo(Role.AGRICULTEUR);
        assertThat(profile.isEnabled()).isTrue();
    }

    @Test
    void getProfile_throwsWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
