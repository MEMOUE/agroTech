package com.agrotech.agroauth.service;

import com.agrotech.agroauth.entity.User;
import com.agrotech.agroauth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // Cherche d'abord par username
        User user = userRepository.findByUsername(login)
                .orElseGet(() -> {
                    // Sinon cherche par email (utile si l'utilisateur entre son email réel)
                    String emailToTry = login.contains("@") ? login.toLowerCase() : toPhoneEmail(login);
                    return userRepository.findByEmail(emailToTry)
                            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + login));
                });

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    /** Transforme un numéro de téléphone en email fictif (même logique que le frontend) */
    private String toPhoneEmail(String phone) {
        return phone.replaceAll("\\s", "") + "@agro.tel";
    }
}
