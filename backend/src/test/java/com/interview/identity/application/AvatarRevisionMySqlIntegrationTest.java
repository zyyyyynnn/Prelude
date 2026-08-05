package com.interview.identity.application;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.avatar.AvatarStorageProperties;
import com.interview.identity.infrastructure.avatar.LocalAvatarStorage;
import com.interview.identity.infrastructure.avatar.LocalLegacyAvatarSource;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses the actual UserProfileServiceImpl and UserMapper against a disposable MySQL 8.4 instance.
 */
class AvatarRevisionMySqlIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("interview_system")
        .withUsername("root")
        .withPassword("root_password");

    private static HikariDataSource dataSource;
    private static SqlSessionTemplate sqlSessionTemplate;
    private static TransactionTemplate transactionTemplate;
    private static UserMapper userMapper;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        executeSchemaScript();

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory sessionFactory = factoryBean.getObject();
        assertThat(sessionFactory).isNotNull();
        sessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        sessionFactory.getConfiguration().addMapper(UserMapper.class);
        sqlSessionTemplate = new SqlSessionTemplate(sessionFactory);
        userMapper = sqlSessionTemplate.getMapper(UserMapper.class);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
        MYSQL.stop();
    }

    @Test
    void latestClaimWinsAndFieldLevelProfileUpdatesSurviveConcurrentAvatarWrites(@TempDir Path root)
        throws Exception {
        seedUser();
        Files.writeString(root.resolve("old.png"), "old");

        AvatarContentProcessor processor = mock(AvatarContentProcessor.class);
        CountDownLatch aProcessing = new CountDownLatch(1);
        CountDownLatch bCommitted = new CountDownLatch(1);
        when(processor.process(any())).thenAnswer(invocation -> {
            AvatarUpload upload = invocation.getArgument(0);
            if (upload.originalFilename().equals("a.png")) {
                aProcessing.countDown();
                assertThat(bCommitted.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return new ProcessedAvatar(
                upload.originalFilename().equals("a.png") ? new byte[] {1, 2, 3} : new byte[] {4, 5, 6},
                "image/png",
                "png",
                1,
                1
            );
        });

        AvatarStorageProperties properties = properties(root);
        LocalAvatarStorage avatarStorage = new LocalAvatarStorage(properties);
        avatarStorage.afterPropertiesSet();
        LocalLegacyAvatarSource legacySource = new LocalLegacyAvatarSource(properties);
        legacySource.afterPropertiesSet();
        UserProfileServiceImpl service = new UserProfileServiceImpl(
            userMapper,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
            processor,
            avatarStorage,
            legacySource,
            transactionTemplate
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<Object> a = executor.submit(() -> runAvatar(service, "a.png"));
            assertThat(aProcessing.await(15, TimeUnit.SECONDS)).isTrue();
            Future<UserProfileResponse> b = executor.submit(() -> {
                UserContext.setCurrentUserId(1L);
                try {
                    return service.updateAvatar(upload("b.png"));
                } finally {
                    UserContext.remove();
                }
            });
            Future<UserProfileResponse> profile = executor.submit(() -> {
                UserContext.setCurrentUserId(1L);
                try {
                    UserProfileRequest request = new UserProfileRequest();
                    request.setUsername("after-profile");
                    request.setThemePreference("dark");
                    return service.updateCurrentUserProfile(request);
                } finally {
                    UserContext.remove();
                }
            });

            UserProfileResponse bResponse = b.get(20, TimeUnit.SECONDS);
            bCommitted.countDown();
            assertThat(profile.get(20, TimeUnit.SECONDS).username()).isEqualTo("after-profile");
            assertThat(a.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(409);

            assertThat(bResponse.avatarUrl()).startsWith("/media/avatars/1_").endsWith(".png");
            User finalUser = userMapper.selectById(1L);
            assertThat(finalUser.getAvatarUrl()).isEqualTo(bResponse.avatarUrl());
            assertThat(finalUser.getAvatarRevision()).isEqualTo(2L);
            assertThat(finalUser.getUsername()).isEqualTo("after-profile");
            assertThat(finalUser.getThemePreference()).isEqualTo("dark");
            assertThat(Files.exists(root.resolve("old.png"))).isFalse();
            assertThat(Files.exists(root.resolve(objectKey(bResponse.avatarUrl())))).isTrue();
            try (var files = Files.list(root)) {
                assertThat(files.filter(path -> path.getFileName().toString().startsWith("1_")).count())
                    .isEqualTo(1);
            }
        }
    }

    private static Object runAvatar(UserProfileServiceImpl service, String filename) {
        UserContext.setCurrentUserId(1L);
        try {
            return service.updateAvatar(upload(filename));
        } catch (Throwable error) {
            return error;
        } finally {
            UserContext.remove();
        }
    }

    private static AvatarUpload upload(String filename) {
        return new AvatarUpload(filename, "image/png", 1, new ByteArrayInputStream(new byte[] {1}));
    }

    private static String objectKey(String avatarUrl) {
        return avatarUrl.substring("/media/avatars/".length());
    }

    private static AvatarStorageProperties properties(Path root) {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setAvatarRoot(root);
        properties.setLegacyAvatarRoot(root);
        properties.setAvatarMaxBytes(1024 * 1024);
        properties.setAvatarMaxWidth(2048);
        properties.setAvatarMaxHeight(2048);
        properties.setAvatarMaxPixels(4_194_304L);
        return properties;
    }

    private static void seedUser() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM `user`");
            statement.executeUpdate("""
                INSERT INTO `user`
                  (`id`, `username`, `password`, `email`, `avatar_url`, `avatar_revision`, `theme_preference`, `llm_provider`, `llm_model`)
                VALUES
                  (1, 'before', 'test-password', 'before@example.test', '/uploads/avatars/old.png', 0, 'system', 'deepseek', 'deepseek-chat')
                """);
        }
        executeSchemaScript();
    }

    private static void executeSchemaScript() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(new ClassPathResource("schema.sql"))
            );
        }
    }
}
