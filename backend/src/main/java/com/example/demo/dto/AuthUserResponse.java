package com.example.demo.dto;

import com.example.demo.model.Account;
import com.example.demo.security.AccountPrincipal;

import java.util.UUID;

public record AuthUserResponse(UUID id, String email, String fullName, String role) {

    public static AuthUserResponse from(Account account) {
        return new AuthUserResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getRole().name());
    }

    public static AuthUserResponse from(AccountPrincipal principal) {
        return new AuthUserResponse(principal.id(), principal.email(), principal.fullName(),
                principal.role().name());
    }
}
