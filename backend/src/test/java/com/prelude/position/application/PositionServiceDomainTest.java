package com.prelude.position.application;

import com.prelude.position.domain.Position;
import com.prelude.position.infrastructure.persistence.PositionMapper;
import com.prelude.test.AccountFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceDomainTest {

    @Mock
    private PositionMapper positionMapper;

    private PositionServiceImpl positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionServiceImpl(positionMapper, AccountFixtures.current(7L));
    }

    @Test
    void listPositionsReturnsDomainEntitiesWithoutApiDtos() {
        Position builtIn = new Position();
        builtIn.setId(1L);
        builtIn.setName("后端");
        builtIn.setAccountId(null);
        Position owned = new Position();
        owned.setId(2L);
        owned.setName("前端");
        owned.setAccountId(7L);
        when(positionMapper.selectList(any())).thenReturn(List.of(builtIn, owned));

        List<Position> positions = positionService.listPositions();

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).getName()).isEqualTo("后端");
        assertThat(positions.get(1).getAccountId()).isEqualTo(7L);
    }
}
