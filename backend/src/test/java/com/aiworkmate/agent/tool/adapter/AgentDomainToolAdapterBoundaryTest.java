package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDomainToolAdapterBoundaryTest {
    private static final List<Class<?>> ADAPTERS = List.of(
            TodoAgentDomainToolAdapter.class,
            LeaveAgentDomainToolAdapter.class,
            ApprovalConfigurationAgentDomainToolAdapter.class,
            ApprovalTaskAgentDomainToolAdapter.class,
            HrOrganizationAgentDomainToolAdapter.class,
            HrEmployeeAgentDomainToolAdapter.class,
            EmployeeChangeAgentDomainToolAdapter.class,
            AttendanceAgentDomainToolAdapter.class,
            AssetAgentDomainToolAdapter.class,
            VisitorAgentDomainToolAdapter.class,
            SealAgentDomainToolAdapter.class,
            MeetingAgentDomainToolAdapter.class,
            FinanceAgentDomainToolAdapter.class,
            PlatformOperationsAgentDomainToolAdapter.class,
            SecurityGovernanceAgentDomainToolAdapter.class,
            OperationalGovernanceAgentDomainToolAdapter.class,
            AgentTaskCenterAgentDomainToolAdapter.class,
            KnowledgeAgentDomainToolAdapter.class,
            NotificationAgentDomainToolAdapter.class);

    private static final List<Class<?>> PORTS = List.of(
            TodoToolPort.class, LeaveToolPort.class, KnowledgeToolPort.class, NotificationToolPort.class,
            ApprovalConfigurationToolPort.class, ApprovalTaskToolPort.class, HrOrganizationToolPort.class,
            HrEmployeeToolPort.class, EmployeeChangeToolPort.class, AttendanceToolPort.class,
            AssetToolPort.class, MeetingToolPort.class, VisitorToolPort.class, SealToolPort.class,
            FinanceToolPort.class, PlatformOperationsToolPort.class, SecurityGovernanceToolPort.class,
            OperationalGovernanceToolPort.class, AgentTaskCenterToolPort.class);

    @Test
    void eachPortHasOneLocalAdapterAndEachAdapterHasOneBusinessBoundary() {
        ADAPTERS.forEach(adapter -> {
            assertThat(adapter).hasAnnotation(Component.class);
            assertThat(adapter.getSimpleName()).endsWith("AgentDomainToolAdapter");
        });
        PORTS.forEach(port -> assertThat(ADAPTERS.stream().filter(port::isAssignableFrom).toList())
                .as(port.getSimpleName())
                .hasSize(1));

        assertThat(TodoAgentDomainToolAdapter.class.getInterfaces()).containsExactly(TodoToolPort.class);
        assertThat(LeaveAgentDomainToolAdapter.class.getInterfaces()).containsExactly(LeaveToolPort.class);
        assertThat(ApprovalConfigurationAgentDomainToolAdapter.class.getInterfaces())
                .containsExactly(ApprovalConfigurationToolPort.class);
        assertThat(ApprovalTaskAgentDomainToolAdapter.class.getInterfaces())
                .containsExactly(ApprovalTaskToolPort.class);
        assertThat(HrOrganizationAgentDomainToolAdapter.class.getInterfaces())
                .containsExactly(HrOrganizationToolPort.class);
        assertThat(HrEmployeeAgentDomainToolAdapter.class.getInterfaces()).containsExactly(HrEmployeeToolPort.class);
        assertThat(EmployeeChangeAgentDomainToolAdapter.class.getInterfaces())
                .containsExactly(EmployeeChangeToolPort.class);
        assertThat(AttendanceAgentDomainToolAdapter.class.getInterfaces()).containsExactly(AttendanceToolPort.class);
        assertThat(AssetAgentDomainToolAdapter.class.getInterfaces()).containsExactly(AssetToolPort.class);
        assertThat(VisitorAgentDomainToolAdapter.class.getInterfaces()).containsExactly(VisitorToolPort.class);
        assertThat(SealAgentDomainToolAdapter.class.getInterfaces()).containsExactly(SealToolPort.class);
        assertThat(MeetingAgentDomainToolAdapter.class.getInterfaces()).containsExactly(MeetingToolPort.class);
        assertThat(FinanceAgentDomainToolAdapter.class.getInterfaces()).containsExactly(FinanceToolPort.class);
        assertThat(PlatformOperationsAgentDomainToolAdapter.class.getInterfaces()).containsExactly(PlatformOperationsToolPort.class);
        assertThat(SecurityGovernanceAgentDomainToolAdapter.class.getInterfaces()).containsExactly(SecurityGovernanceToolPort.class);
        assertThat(OperationalGovernanceAgentDomainToolAdapter.class.getInterfaces()).containsExactly(OperationalGovernanceToolPort.class);
        assertThat(AgentTaskCenterAgentDomainToolAdapter.class.getInterfaces()).containsExactly(AgentTaskCenterToolPort.class);
        assertThat(KnowledgeAgentDomainToolAdapter.class.getInterfaces()).containsExactly(KnowledgeToolPort.class);
        assertThat(NotificationAgentDomainToolAdapter.class.getInterfaces()).containsExactly(NotificationToolPort.class);
    }
}
