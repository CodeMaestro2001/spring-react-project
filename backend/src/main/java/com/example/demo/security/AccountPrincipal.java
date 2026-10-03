package com.example.demo.security;

import com.example.demo.model.Account;
import com.example.demo.model.AccountRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AccountPrincipal(UUID id, String email, String fullName, String passwordHash,
                               AccountRole role) implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    public static AccountPrincipal from(Account account) {
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getFullName(),
                account.getPasswordHash(), account.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
