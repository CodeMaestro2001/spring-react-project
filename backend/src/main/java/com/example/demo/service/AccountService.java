package com.example.demo.service;

import com.example.demo.dto.AuthUserResponse;
import com.example.demo.model.Account;
import com.example.demo.model.AccountRole;
import com.example.demo.repo.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthUserResponse register(String email, String fullName, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Account account = new Account(normalizedEmail, fullName.trim(), passwordEncoder.encode(password),
                AccountRole.CUSTOMER);
        try {
            return AuthUserResponse.from(accountRepository.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
