package com.prelude.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
class DevAccountInitializer implements ApplicationRunner {

    static final String USERNAME = "demo";
    static final String PASSWORD = "123456";

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
            .eq(Account::getUsername, USERNAME)
            .last("LIMIT 1"));
        if (account == null) {
            account = new Account();
            account.setUsername(USERNAME);
            account.setEmail("demo@example.com");
            account.setPasswordHash(passwordEncoder.encode(PASSWORD));
            account.setRevision(0L);
            accountMapper.insert(account);
            return;
        }
        if (account.getPasswordHash() == null || !passwordEncoder.matches(PASSWORD, account.getPasswordHash())) {
            account.setPasswordHash(passwordEncoder.encode(PASSWORD));
            accountMapper.updateById(account);
        }
    }
}
