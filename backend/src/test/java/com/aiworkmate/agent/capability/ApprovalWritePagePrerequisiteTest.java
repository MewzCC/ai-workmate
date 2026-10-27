package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.ToolCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovalWritePagePrerequisiteTest {
    private final PageCapabilityCatalog pages = new PageCapabilityCatalog();
    @ParameterizedTest
    @EnumSource(value=ToolCode.class,names={"APPROVAL_APPLICATION_CREATE_DRAFT","APPROVAL_APPLICATION_UPDATE_DRAFT","APPROVAL_APPLICATION_SUBMIT_DRAFT","APPROVAL_APPLICATION_CANCEL_DRAFT","APPROVAL_APPLICATION_WITHDRAW","APPROVAL_APPLICATION_REOPEN","APPROVAL_APPLICATION_REMIND"})
    void requiresTheExistingDomainEntryBeforeOfferingAWrite(ToolCode code) {
        var permissions = new LinkedHashSet<String>();
        permissions.add("route:ai-workspace");
        permissions.add("route:my-applications");
        assertThat(pages.prerequisitePageIdsForTool(code.code())).isEqualTo(Set.of("approval-start"));
        assertThat(pages.allowsTool("ai-workspace",code.code(),permissions)).isFalse();
        permissions.add("route:approval-start");
        assertThat(pages.allowsTool("ai-workspace",code.code(),permissions)).isTrue();
        permissions.remove("route:approval-start");
        assertThat(pages.allowsTool("my-applications",code.code(),permissions)).isFalse();
    }
}
