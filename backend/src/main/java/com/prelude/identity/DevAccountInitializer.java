package com.prelude.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
class DevAccountInitializer implements ApplicationRunner {

    static final String USERNAME = "demo";
    static final String PASSWORD = "123456";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, USERNAME)
            .last("LIMIT 1"));
        if (user == null) {
            user = new User();
            user.setUsername(USERNAME);
            user.setEmail("demo@example.com");
            user.setPassword(passwordEncoder.encode(PASSWORD));
            userMapper.insert(user);
            return;
        }
        if (!passwordEncoder.matches(PASSWORD, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(PASSWORD));
            userMapper.updateById(user);
        }
    }
}
