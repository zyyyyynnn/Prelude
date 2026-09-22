package com.prelude.position.application;

import com.prelude.BusinessException;
import com.prelude.position.application.port.PositionRepository;
import com.prelude.position.domain.Position;
import com.prelude.test.AccountFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceDomainTest {

    @Mock
    private PositionRepository positions;

    private PositionServiceImpl positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionServiceImpl(positions, AccountFixtures.current(7L));
    }

    @Test
    void listPositionsIsScopedToTheCurrentAccount() {
        when(positions.listAccessible(7L)).thenReturn(List.of(position(2L, 7L, "前端")));

        assertThat(positionService.listPositions()).extracting(Position::getName).containsExactly("前端");
    }

    @Test
    void createTrimsTheStoredNameAndPrompt() {
        when(positions.nameTaken("Java 后端工程师", null)).thenReturn(false);

        positionService.createPosition("  Java 后端工程师  ", "  重点考察并发  ");

        ArgumentCaptor<Position> saved = ArgumentCaptor.forClass(Position.class);
        verify(positions).add(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Java 后端工程师");
        assertThat(saved.getValue().getSystemPrompt()).isEqualTo("重点考察并发");
        assertThat(saved.getValue().getAccountId()).isEqualTo(7L);
    }

    @Test
    void createRejectsADuplicateNameBeforeWriting() {
        when(positions.nameTaken("Java 后端工程师", null)).thenReturn(true);

        assertThatThrownBy(() -> positionService.createPosition("Java 后端工程师", "prompt"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("同名岗位已存在");
        verify(positions, never()).add(any());
    }

    @Test
    void updateRejectsAPositionTheCallerDoesNotOwn() {
        when(positions.findOwned(7L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> positionService.updatePosition(1L, "新名字", "prompt"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("岗位不存在或不可编辑");
        verify(positions, never()).change(any());
    }

    @Test
    void deleteReportsABadRequestWhenTheRowIsStillReferenced() {
        when(positions.findOwned(7L, 3L)).thenReturn(position(3L, 7L, "算法"));
        doThrow(new DataIntegrityViolationException("fk"))
            .when(positions)
            .remove(3L);

        assertThatThrownBy(() -> positionService.deletePosition(3L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("该岗位已被面试使用，无法删除");
    }

    private static Position position(Long id, Long accountId, String name) {
        Position position = new Position();
        position.setId(id);
        position.setAccountId(accountId);
        position.setName(name);
        position.setSystemPrompt("prompt");
        return position;
    }
}
