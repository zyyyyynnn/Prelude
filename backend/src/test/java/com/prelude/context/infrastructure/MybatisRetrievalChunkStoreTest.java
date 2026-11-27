package com.prelude.context.infrastructure;

import com.prelude.context.RetrievalChunkStore;
import com.prelude.context.RetrievalTransactions;
import com.prelude.context.infrastructure.persistence.MybatisRetrievalChunkStore;
import com.prelude.context.infrastructure.persistence.RetrievalChunkEntity;
import com.prelude.context.infrastructure.persistence.RetrievalChunkMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MybatisRetrievalChunkStoreTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private RetrievalChunkStore store;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("CREATE TABLE retrieval_chunk ("
            + "scope_type VARCHAR(32), scope_id BIGINT, ordinal INT, content VARCHAR(1024), "
            + "content_hash VARCHAR(64), embedding_model VARCHAR(64), embedding_dimensions INT, embedding_json VARCHAR(1024))");
        jdbcTemplate.execute("CREATE TABLE retrieval_scope_tombstone ("
            + "scope_type VARCHAR(32), scope_id BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "PRIMARY KEY (scope_type, scope_id))");
        RetrievalChunkMapper mapper = mock(RetrievalChunkMapper.class);
        doAnswer(invocation -> {
            jdbcTemplate.update("DELETE FROM retrieval_chunk");
            return 0;
        }).when(mapper).delete(any());
        doAnswer(invocation -> {
            RetrievalChunkEntity row = invocation.getArgument(0);
            jdbcTemplate.update(
                "INSERT INTO retrieval_chunk (scope_type, scope_id, ordinal, content, content_hash, "
                    + "embedding_model, embedding_dimensions, embedding_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                row.getScopeType(), row.getScopeId(), row.getOrdinal(), row.getContent(), row.getContentHash(),
                row.getEmbeddingModel(), row.getEmbeddingDimensions(), row.getEmbeddingJson()
            );
            return 1;
        }).when(mapper).insert(any(RetrievalChunkEntity.class));
        doAnswer(invocation -> {
            String scopeType = invocation.getArgument(0);
            Long scopeId = invocation.getArgument(1);
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM retrieval_scope_tombstone WHERE scope_type = ? AND scope_id = ?",
                Integer.class, scopeType, scopeId);
            if (count == null || count == 0) {
                jdbcTemplate.update(
                    "INSERT INTO retrieval_scope_tombstone (scope_type, scope_id) VALUES (?, ?)",
                    scopeType, scopeId);
            }
            return null;
        }).when(mapper).insertTombstone(anyString(), any());
        doAnswer(invocation -> {
            String scopeType = invocation.getArgument(0);
            Long scopeId = invocation.getArgument(1);
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM retrieval_scope_tombstone WHERE scope_type = ? AND scope_id = ?",
                Integer.class, scopeType, scopeId);
            return count == null ? 0 : count;
        }).when(mapper).countTombstones(anyString(), any());
        MybatisRetrievalChunkStore target = new MybatisRetrievalChunkStore(mapper, mock(ObjectMapper.class));
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(database);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        store = (RetrievalChunkStore) proxy.getProxy();
    }

    @AfterEach
    void shutDownDatabase() {
        database.shutdown();
    }

    @Test
    void afterCommitReplacementPersistsInItsOwnTransaction() {
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(database));
        transactions.executeWithoutResult(status -> {
            RetrievalTransactions.afterCommit(() -> store.replace(
                "session",
                42L,
                List.of(new RetrievalChunkStore.StoredChunk(0, "chunk", "hash", null, null))
            ));
            assertThat(chunkCount()).isZero();
        });

        assertThat(chunkCount()).isEqualTo(1);
    }

    @Test
    void afterCommitDeletionPersistsInItsOwnTransaction() {
        jdbcTemplate.update("INSERT INTO retrieval_chunk (scope_type, scope_id, content) VALUES ('session', 42, 'chunk')");
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(database));
        transactions.executeWithoutResult(status -> RetrievalTransactions.afterCommit(() -> store.delete("session", 42L)));

        assertThat(chunkCount()).isZero();
    }

    @Test
    void tombstoneRejectsLateReplacementAndPersistedReload() {
        store.markInvalidated("session", 42L);
        store.replace("session", 42L, List.of(
            new RetrievalChunkStore.StoredChunk(0, "late", "hash", null, null)
        ));

        assertThat(store.isInvalidated("session", 42L)).isTrue();
        assertThat(store.load("session", 42L)).isEmpty();
        assertThat(chunkCount()).isZero();
    }

    @Test
    void supportsPropagationRunsAfterCommitActionImmediately() {
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(database));
        transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_SUPPORTS);
        transactions.executeWithoutResult(status -> RetrievalTransactions.afterCommit(() -> ran.set(true)));

        assertThat(ran).isTrue();
    }

    private int chunkCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_chunk", Integer.class);
        return count == null ? 0 : count;
    }
}
