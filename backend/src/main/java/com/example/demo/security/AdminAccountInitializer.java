package com.example.demo.security;

import com.example.demo.model.Account;
import com.example.demo.model.AccountRole;
import com.example.demo.repo.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountInitializer(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.email:}") String email,
                                   @Value("${app.admin.password:}") String password) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank() || password.length() < 12 || password.length() > 72
                || !email.contains("@")) {
            throw new IllegalStateException("Configure both APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD; use a valid email and a 12–72 character password.");
        }

        accountRepository.findByEmail(email).ifPresentOrElse(account -> {
            if (account.getRole() != AccountRole.ADMIN) {
                throw new IllegalStateException("The configured administrator email belongs to a non-admin account.");
            }
        }, () -> accountRepository.saveAndFlush(
                new Account(email, "SupplyCart Administrator", passwordEncoder.encode(password), AccountRole.ADMIN)));
    }
}
