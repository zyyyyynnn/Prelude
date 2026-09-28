package com.prelude.resume.application;

import com.prelude.resume.api.ResumeConversationResponse;
import com.prelude.resume.infrastructure.persistence.ResumeAssistantMessageMapper;
import com.prelude.resume.infrastructure.persistence.ResumeConversationEntity;
import com.prelude.resume.infrastructure.persistence.ResumeConversationMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolCallMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolDiffMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolGroupMapper;
import com.prelude.resume.infrastructure.persistence.ResumeTurnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeWorkspaceServiceTest {

    @Mock
    private ResumeConversationMapper conversations;
    @Mock
    private ResumeTurnMapper turns;
    @Mock
    private ResumeAssistantMessageMapper messages;
    @Mock
    private ResumeToolGroupMapper toolGroups;
    @Mock
    private ResumeToolCallMapper toolCalls;
    @Mock
    private ResumeToolDiffMapper toolDiffs;

    private ResumeWorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new ResumeWorkspaceService(
            conversations,
            turns,
            messages,
            toolGroups,
            toolCalls,
            toolDiffs,
            new ObjectMapper()
        );
    }

    @Test
    void listConversationsGroupsByTurnActivity() {
        when(conversations.listByOwner(7L)).thenReturn(List.of(conversation(1L), conversation(2L), conversation(3L)));
        when(turns.findActiveConversationIds(List.of(1L, 2L, 3L))).thenReturn(List.of(2L));
        when(turns.findConversationIdsWithTurns(List.of(1L, 2L, 3L))).thenReturn(List.of(1L, 2L));

        assertThat(service.listConversations(7L))
            .extracting(ResumeConversationResponse::id, ResumeConversationResponse::status)
            .containsExactly(
                tuple(1L, "finished"),
                tuple(2L, "active"),
                tuple(3L, "active")
            );
    }

    @Test
    void listConversationsOfAnEmptyAccountAsksNothingOfTheTurnTable() {
        when(conversations.listByOwner(7L)).thenReturn(List.of());

        assertThat(service.listConversations(7L)).isEmpty();
    }

    @Test
    void createConversationStartsActive() {
        ResumeConversationResponse created = service.createConversation(7L, null);

        ArgumentCaptor<ResumeConversationEntity> saved = ArgumentCaptor.forClass(ResumeConversationEntity.class);
        verify(conversations).insert(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(7L);
        assertThat(created.status()).isEqualTo("active");
        assertThat(created.title()).isEqualTo("新的简历工作");
    }

    @Test
    void pinConversationUpdatesPinnedTimestamp() {
        ResumeConversationEntity entity = conversation(3L);
        when(conversations.findOwned(3L, 7L)).thenReturn(entity);

        service.pinConversation(7L, 3L, true);

        assertThat(entity.getPinnedAt()).isNotNull();
        verify(conversations).updateById(entity);
    }

    @Test
    void unpinConversationClearsPinnedTimestamp() {
        ResumeConversationEntity entity = conversation(3L);
        entity.setPinnedAt(LocalDateTime.now());
        when(conversations.findOwned(3L, 7L)).thenReturn(entity);

        service.pinConversation(7L, 3L, false);

        assertThat(entity.getPinnedAt()).isNull();
        verify(conversations).updateById(entity);
    }

    @Test
    void pinConversationRejectsUnownedConversation() {
        when(conversations.findOwned(3L, 7L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.pinConversation(7L, 3L, true))
            .isInstanceOf(com.prelude.BusinessException.class);
    }

    @Test
    void deleteConversationRemovesRow() {
        ResumeConversationEntity entity = conversation(5L);
        when(conversations.findOwned(5L, 7L)).thenReturn(entity);

        service.deleteConversation(7L, 5L);

        verify(conversations).deleteById(5L);
    }

    @Test
    void deleteConversationRejectsUnownedConversation() {
        when(conversations.findOwned(5L, 7L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteConversation(7L, 5L))
            .isInstanceOf(com.prelude.BusinessException.class);
    }

    private static ResumeConversationEntity conversation(Long id) {
        ResumeConversationEntity entity = new ResumeConversationEntity();
        entity.setId(id);
        entity.setAccountId(7L);
        entity.setTitle("简历工作 " + id);
        entity.setCreatedAt(LocalDateTime.parse("2026-09-26T10:00:00"));
        entity.setUpdatedAt(LocalDateTime.parse("2026-09-26T10:00:00"));
        return entity;
    }
}
