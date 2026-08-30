package com.prelude;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.modulith.Modulithic;
import org.mybatis.spring.annotation.MapperScan;

@Modulithic
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan(value = "com.prelude", markerInterface = BaseMapper.class)
public class PreludeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PreludeApplication.class, args);
    }
}
