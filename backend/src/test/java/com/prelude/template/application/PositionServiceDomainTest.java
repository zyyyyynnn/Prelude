package com.prelude.template.application;

import com.prelude.identity.api.CurrentAccount;
import com.prelude.template.domain.PositionTemplate;
import com.prelude.template.infrastructure.persistence.PositionTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceDomainTest {

    @Mock
    private PositionTemplateMapper positionTemplateMapper;
    @Mock
    private CurrentAccount currentAccount;

    @InjectMocks
    private PositionServiceImpl positionService;

    @Test
    void listPositionsReturnsDomainEntitiesWithoutApiDtos() {
        when(currentAccount.requireId()).thenReturn(7L);
        PositionTemplate builtIn = new PositionTemplate();
        builtIn.setId(1L);
        builtIn.setName("后端");
        builtIn.setAccountId(null);
        PositionTemplate owned = new PositionTemplate();
        owned.setId(2L);
        owned.setName("前端");
        owned.setAccountId(7L);
        when(positionTemplateMapper.selectList(any())).thenReturn(List.of(builtIn, owned));

        List<PositionTemplate> positions = positionService.listPositions();

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).getName()).isEqualTo("后端");
        assertThat(positions.get(1).getAccountId()).isEqualTo(7L);
    }
}
