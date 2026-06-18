package com.agrotech.agroauth.service;

import com.agrotech.agroauth.entity.Role;
import com.agrotech.agroauth.entity.User;
import com.agrotech.agroauth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_returnsUserDetails() {
        User user = User.builder()
                .username("john")
                .password("hashed")
                .role(Role.AGRICULTEUR)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("john");

        assertThat(details.getUsername()).isEqualTo("john");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_AGRICULTEUR"));
    }

    @Test
    void loadUserByUsername_disabledUserIsNotEnabled() {
        User user = User.builder()
                .username("john")
                .password("hashed")
                .role(Role.AGRICULTEUR)
                .enabled(false)
                .build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("john");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_throwsWhenNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void loadUserByUsername_setsCorrectRoleAuthority() {
        User admin = User.builder()
                .username("admin")
                .password("hashed")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
