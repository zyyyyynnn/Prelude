package com.prelude;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:prelude;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "prelude.jobs.recovery-enabled=false"
})
class PreludeApplicationTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void startsSpringAndMybatisOnBoot4() throws Exception {
        try (var session = sqlSessionFactory.openSession();
             var statement = session.getConnection().createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
