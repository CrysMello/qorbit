package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapter entre QorbitUser e Spring Security UserDetails.
 * Carregado uma vez por requisição autenticada.
 */
@Service
@Transactional(readOnly = true)
public class QorbitUserDetailsService implements UserDetailsService {

    private final QorbitUserRepository userRepo;

    public QorbitUserDetailsService(QorbitUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        QorbitUser user = userRepo.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        return User.builder()
            .username(user.getEmail())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .accountExpired(false)
            .accountLocked(user.isAccountLocked()
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(java.time.LocalDateTime.now()))
            .credentialsExpired(user.getPasswordExpiresAt() != null
                && user.getPasswordExpiresAt().isBefore(java.time.LocalDateTime.now()))
            .disabled(!user.isActive() || !user.isEmailVerified())
            .build();
    }
}
