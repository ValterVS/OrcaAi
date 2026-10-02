package com.orcaai.identity;

import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) {
        return users.findByEmail(User.normalizeEmail(email))
                .map(user -> new AuthenticatedUser(
                        user.getId(),
                        user.getOrganizationId(),
                        user.getEmail(),
                        user.getPasswordHash(),
                        user.getRole(),
                        user.isEnabled()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
