package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.AttendanceStatisticsResponse;
import com.aiworkmate.service.AttendanceService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AttendanceAgentDomainToolAdapterTest {
    @Test void personalOnlyStatisticsDoNotRequireAnAuthorizedTeamList() {
        var service = mock(AttendanceService.class);
        var start = LocalDate.of(2026,10,1);
        when(service.getStatistics(2L,2026,10)).thenReturn(new AttendanceStatisticsResponse(start,start.plusDays(30),
                new AttendanceStatisticsResponse.PersonalStats(2L,"Reader",1,1,0,0,0,0),null));
        var query = new AttendanceToolPort.Query(AttendanceToolPort.Resource.STATISTICS,null,null,null,null,2026,10,1,20);
        var result = new AttendanceAgentDomainToolAdapter(service).query(new ToolActorContext(1L,2L,3L,4L,0,"trace"),query);
        assertThat(result.statistics().personal().employeeName()).isEqualTo("Reader");
        assertThat(result.statistics().team()).isEmpty();
        verify(service).getStatistics(2L,2026,10);
    }
}
