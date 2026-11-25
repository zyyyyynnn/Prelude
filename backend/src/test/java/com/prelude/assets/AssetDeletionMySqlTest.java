package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.application.port.AssetDeletionStore;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.infrastructure.MybatisAssetDeletionStore;
import com.prelude.assets.infrastructure.persistence.AssetDeletionRequestMapper;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import com.prelude.interview.application.DeleteInterviewSession;
import com.prelude.resume.application.DeleteResume;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.test.AccountFixtures;
import com.prelude.test.InterviewDataFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.storage.deletion.scheduling-enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AssetDeletionMySqlTest {

    @MockitoBean
    private ObjectStoragePort objectStoragePort;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AssetDeletionService assetDeletionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AssetDeletionRequestMapper deletionRequestMapper;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private DeleteInterviewSession deleteInterviewSession;

    @Autowired
    private DeleteResume deleteResume;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDeletionQueue() {
        jdbcTemplate.update("DELETE FROM asset_deletion_request");
    }

    @Test
    void deleteScopePersistsIdempotentIntentAndWorkerRemovesObjectThenMetadata() {
        long accountId = AccountFixtures.create(jdbcTemplate, "scope-delete");
        AccountFixtures.authenticate(accountId);
        long firstAssetId = uploadAndBind(accountId, 42L, "first.txt");
        long secondAssetId = uploadAndBind(accountId, 42L, "second.txt");
        String firstKey = objectKey(firstAssetId);
        String secondKey = objectKey(secondAssetId);

        attachmentService.deleteScope(accountId, "interview", 42L);
        attachmentService.deleteScope(accountId, "interview", 42L);

        assertThat(count("SELECT COUNT(*) FROM attachment WHERE account_id = ? AND scope_type = 'interview' AND scope_id = ?",
            accountId, 42L)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset WHERE id IN (?, ?)", firstAssetId, secondAssetId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE account_id = ?", accountId)).isEqualTo(2);
        verify(objectStoragePort, never()).delete(firstKey);
        verify(objectStoragePort, never()).delete(secondKey);

        assetDeletionService.processNextBatch();
        assetDeletionService.processNextBatch();

        assertThat(count("SELECT COUNT(*) FROM asset WHERE id IN (?, ?)", firstAssetId, secondAssetId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE account_id = ?", accountId)).isZero();
        verify(objectStoragePort, times(1)).delete(firstKey);
        verify(objectStoragePort, times(1)).delete(secondKey);
    }

    @Test
    void failedDiscardRollsBackTheCallerTransactionAndDeletionRegistration() {
        long accountId = AccountFixtures.create(jdbcTemplate, "registration-transaction");
        AccountFixtures.authenticate(accountId);
        long assetId = uploadAndBind(accountId, 49L, "avatar.txt");
        jdbcTemplate.update("UPDATE asset SET kind = 'avatar', status = 'READY' WHERE id = ?", assetId);
        String objectKey = objectKey(assetId);
        AssetRow asset = new AssetRow(assetId, accountId, "avatar", objectKey, "text/plain", 1L, "READY");
        MybatisAssetDeletionStore realStore = new MybatisAssetDeletionStore(deletionRequestMapper, assetMapper);
        AssetDeletionStore store = spy(realStore);
        doAnswer(invocation -> {
            realStore.register(accountId, assetId, objectKey);
            throw new IllegalStateException("registration unavailable");
        }).when(store).register(accountId, assetId, objectKey);
        AssetLookup lookup = mock(AssetLookup.class);
        when(lookup.findById(assetId)).thenReturn(asset);
        AssetDeletionService service = new AssetDeletionService(
            store, lookup, objectStoragePort,
            new AssetDeletionProperties(10, Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(1)));
        AvatarAssetStorage storage = new AvatarAssetStorage(
            mock(AssetService.class), lookup, objectStoragePort, new AvatarImageValidator(),
            transactionalProxy(service));
        AvatarAssetStorage transactionalStorage = transactionalProxy(storage);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        String originalUsername = jdbcTemplate.queryForObject(
            "SELECT username FROM user_account WHERE id = ?", String.class, accountId);
        String updatedUsername = "rolled-back-profile-" + System.nanoTime();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE user_account SET username = ? WHERE id = ?", updatedUsername, accountId);
            transactionalStorage.discard(accountId, "/api/assets/" + assetId + "/content");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("registration unavailable");

        assertThat(jdbcTemplate.queryForObject("SELECT username FROM user_account WHERE id = ?", String.class, accountId))
            .isEqualTo(originalUsername);
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id = ?", assetId)).isZero();
    }

    @Test
    void concurrentRegistrationValidatesTheWinnerWithACurrentRead() throws Exception {
        long accountId = AccountFixtures.create(jdbcTemplate, "scope-register-race");
        AccountFixtures.authenticate(accountId);
        long assetId = uploadAndBind(accountId, 47L, "registration.txt");
        String key = objectKey(assetId);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstInsertStarted = new CountDownLatch(1);
        CountDownLatch allowFirstInsert = new CountDownLatch(1);
        AtomicBoolean pauseFirstInsert = new AtomicBoolean(true);
        AssetDeletionRequestMapper pausingMapper = (AssetDeletionRequestMapper) Proxy.newProxyInstance(
            AssetDeletionRequestMapper.class.getClassLoader(),
            new Class<?>[] {AssetDeletionRequestMapper.class},
            (proxy, method, arguments) -> {
                if ("insert".equals(method.getName()) && pauseFirstInsert.getAndSet(false)) {
                    firstInsertStarted.countDown();
                    assertThat(allowFirstInsert.await(5, TimeUnit.SECONDS)).isTrue();
                }
                try {
                    return method.invoke(deletionRequestMapper, arguments);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            });
        AssetDeletionStore concurrentStore = new MybatisAssetDeletionStore(pausingMapper, assetMapper);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status ->
                concurrentStore.register(accountId, assetId, key)));
            assertThat(firstInsertStarted.await(5, TimeUnit.SECONDS)).isTrue();

            transactions.executeWithoutResult(status -> concurrentStore.register(accountId, assetId, key));
            allowFirstInsert.countDown();
            first.get(5, TimeUnit.SECONDS);
        }

        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id = ?", assetId)).isEqualTo(1);
    }

    @Test
    void storageFailureLeavesRecoverableMetadataUntilTheNextDueAttempt() {
        long accountId = AccountFixtures.create(jdbcTemplate, "scope-retry");
        AccountFixtures.authenticate(accountId);
        long assetId = uploadAndBind(accountId, 43L, "retry.txt");
        String key = objectKey(assetId);
        attachmentService.deleteScope(accountId, "interview", 43L);
        doThrow(new IllegalStateException("gateway unavailable"))
            .doNothing()
            .when(objectStoragePort).delete(key);

        assetDeletionService.processNextBatch();

        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isEqualTo(1);
        assertThat(requestStatus(assetId)).isEqualTo("PENDING");
        assertThat(requestAttempts(assetId)).isEqualTo(1);

        jdbcTemplate.update("UPDATE asset_deletion_request SET next_attempt_at = ? WHERE asset_id = ?",
            LocalDateTime.now().minusSeconds(1), assetId);
        assetDeletionService.processNextBatch();

        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id = ?", assetId)).isZero();
        verify(objectStoragePort, times(2)).delete(key);
    }

    @Test
    void expiredWorkerLeaseIsReclaimedWithoutLosingTheDeletionIntent() {
        long accountId = AccountFixtures.create(jdbcTemplate, "scope-lease");
        AccountFixtures.authenticate(accountId);
        long assetId = uploadAndBind(accountId, 44L, "lease.txt");
        String key = objectKey(assetId);
        attachmentService.deleteScope(accountId, "interview", 44L);
        jdbcTemplate.update(
            "UPDATE asset_deletion_request SET status = 'RUNNING', attempt_count = 1, lease_expires_at = ? WHERE asset_id = ?",
            LocalDateTime.now().minusSeconds(1), assetId);

        assetDeletionService.processNextBatch();

        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id = ?", assetId)).isZero();
        verify(objectStoragePort).delete(key);
    }

    @Test
    void crossAccountRequestIsBlockedWithoutDeletingTheObjectOrAsset() {
        long owner = AccountFixtures.create(jdbcTemplate, "scope-owner");
        long intruder = AccountFixtures.create(jdbcTemplate, "scope-intruder");
        AccountFixtures.authenticate(owner);
        long assetId = uploadAndBind(owner, 45L, "owned.txt");
        String key = objectKey(assetId);
        jdbcTemplate.update(
            "INSERT INTO asset_deletion_request (account_id, asset_id, object_key) VALUES (?, ?, ?)",
            intruder, assetId, key);

        assetDeletionService.processNextBatch();

        verify(objectStoragePort, never()).delete(key);
        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isEqualTo(1);
        assertThat(requestStatus(assetId)).isEqualTo("BLOCKED");
    }

    @Test
    void mismatchedAttachmentOwnershipRollsBackScopeMetadataDeletion() {
        long owner = AccountFixtures.create(jdbcTemplate, "attachment-owner");
        long other = AccountFixtures.create(jdbcTemplate, "attachment-other");
        AccountFixtures.authenticate(owner);
        long assetId = uploadAndBind(owner, 46L, "mismatch.txt");
        jdbcTemplate.update("UPDATE attachment SET account_id = ? WHERE asset_id = ?", other, assetId);
        AccountFixtures.authenticate(other);

        assertThatThrownBy(() -> attachmentService.deleteScope(other, "interview", 46L))
            .isInstanceOf(BusinessException.class);

        assertThat(count("SELECT COUNT(*) FROM attachment WHERE asset_id = ?", assetId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id = ?", assetId)).isZero();
    }

    @Test
    void aLaterScopeFailureRollsBackEarlierDeletionRegistrations() {
        long owner = AccountFixtures.create(jdbcTemplate, "scope-registration-rollback");
        long other = AccountFixtures.create(jdbcTemplate, "scope-registration-owner");
        AccountFixtures.authenticate(owner);
        long firstAssetId = uploadAndBind(owner, 48L, "first.txt");
        long secondAssetId = uploadAndBind(owner, 48L, "second.txt");
        jdbcTemplate.update("UPDATE asset SET account_id = ? WHERE id = ?", other, secondAssetId);

        assertThatThrownBy(() -> attachmentService.deleteScope(owner, "interview", 48L))
            .isInstanceOf(BusinessException.class);

        assertThat(count("SELECT COUNT(*) FROM attachment WHERE account_id = ? AND scope_id = ?", owner, 48L))
            .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE asset_id IN (?, ?)",
            firstAssetId, secondAssetId)).isZero();
    }

    @Test
    void interviewSessionDeletionRegistersItsAttachmentAssetsBeforeReturning() {
        long accountId = AccountFixtures.create(jdbcTemplate, "session-delete");
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbcTemplate, accountId, LocalDateTime.now());
        AccountFixtures.authenticate(accountId);
        long assetId = uploadAndBind(accountId, sessionId, "session.txt");
        String key = objectKey(assetId);

        deleteInterviewSession.execute(sessionId);

        assertThat(count("SELECT COUNT(*) FROM interview_session WHERE id = ?", sessionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM attachment WHERE asset_id = ?", assetId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE account_id = ? AND asset_id = ?",
            accountId, assetId)).isEqualTo(1);
        verify(objectStoragePort, never()).delete(key);

        assetDeletionService.processNextBatch();

        assertThat(count("SELECT COUNT(*) FROM asset WHERE id = ?", assetId)).isZero();
        verify(objectStoragePort).delete(key);
    }

    @Test
    void resumeDeletionHasNoAssetOrObjectToPropagate() {
        long accountId = AccountFixtures.create(jdbcTemplate, "resume-delete");
        ResumeRepository.StoredResume resume = resumeRepository.create(new ResumeRepository.NewResume(
            accountId, "resume.pdf", "resume text", List.of("Java"), List.of()));

        deleteResume.execute(accountId, resume.id());

        assertThat(count("SELECT COUNT(*) FROM resume WHERE id = ?", resume.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM asset_deletion_request WHERE account_id = ?", accountId)).isZero();
    }

    private long uploadAndBind(long accountId, long scopeId, String fileName) {
        AttachmentSnapshot snapshot = attachmentService.upload(
            fileName, "text/plain", fileName.getBytes(StandardCharsets.UTF_8));
        attachmentService.bind(accountId, List.of(snapshot.id()), "interview", scopeId);
        return snapshot.assetRef().id();
    }

    private String objectKey(long assetId) {
        return jdbcTemplate.queryForObject("SELECT object_key FROM asset WHERE id = ?", String.class, assetId);
    }

    private String requestStatus(long assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM asset_deletion_request WHERE asset_id = ?", String.class, assetId);
    }

    private int requestAttempts(long assetId) {
        Integer attempts = jdbcTemplate.queryForObject(
            "SELECT attempt_count FROM asset_deletion_request WHERE asset_id = ?", Integer.class, assetId);
        return attempts == null ? 0 : attempts;
    }

    @SuppressWarnings("unchecked")
    private <T> T transactionalProxy(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    @AfterEach
    void forgetTheAuthenticatedAccount() {
        AccountFixtures.clearAuthentication();
    }
}
