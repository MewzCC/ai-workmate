package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GenericApprovalPlanTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void skipsFrameNodesAndFreezesEverySequentialAssigneeInsteadOfDefaultingStartToTheManager() throws Exception {
        var plan = GenericApprovalPlan.compile(json, """
                [{"nodeType":"START","nodeName":"Start"},{"nodeType":"APPROVAL","nodeName":"Review","approveType":"ROLE","mode":"SEQUENTIAL"},{"nodeType":"END","nodeName":"End"}]
                """, 48, node -> List.of(11L, 12L));
        assertThat(plan.steps()).containsExactly(new GenericApprovalPlan.Step(0,"Review",11L,48), new GenericApprovalPlan.Step(1,"Review",12L,48));
        assertThat(GenericApprovalPlan.decode(json, json.writeValueAsString(plan))).isEqualTo(plan);
    }
    @ParameterizedTest @ValueSource(strings={"CONDITION","CC","DELAY","SELF"})
    void refusesUnsupportedNodesRatherThanSilentlyAdvancing(String kind) {
        String node = "[{\"nodeName\":\"Unsafe\",\"nodeType\":\"" + kind + "\"}]";
        assertThatThrownBy(() -> GenericApprovalPlan.compile(json, node,48,n -> List.of(11L))).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsParallelGroupsAutoApprovalDuplicateKeysAndOversizedPlans() {
        for (String node : List.of("[{\"nodeName\":\"Group\",\"mode\":\"OR_SIGN\"}]",
                "[{\"nodeName\":\"Timer\",\"timeoutEnabled\":true,\"timeoutHours\":1,\"timeoutAction\":\"AUTO_APPROVE\"}]",
                "[{\"nodeName\":\"One\",\"nodeName\":\"Duplicate\"}]", "[] {}"))
            assertThatThrownBy(() -> GenericApprovalPlan.compile(json,node,48,n -> List.of(11L,12L))).isInstanceOf(BusinessException.class);
        String many = "[" + String.join(",", java.util.Collections.nCopies(20,"{\"nodeName\":\"Review\"}")) + "]";
        assertThatThrownBy(() -> GenericApprovalPlan.compile(json,many,48,n -> List.of(11L,12L,13L))).isInstanceOf(BusinessException.class);
    }
    @Test void matchingRulesBlockApprovalAndNonmatchingRulesDoNotInventAnAdditionalNode() {
        String rules = "[{\"conditionJson\":\"{\\\"field\\\":\\\"amount\\\",\\\"op\\\":\\\"gte\\\",\\\"value\\\":5000}\",\"actionJson\":\"{\\\"enabled\\\":true,\\\"appendNode\\\":\\\"FINANCE_REVIEW\\\"}\"}]";
        assertThatCode(() -> GenericApprovalPlan.requireRulesHandled(json,rules,"{\"amount\":12.50}")).doesNotThrowAnyException();
        assertThatThrownBy(() -> GenericApprovalPlan.requireRulesHandled(json,rules,"{\"amount\":\"5000.000\"}")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GenericApprovalPlan.requireRulesHandled(json,"{}","{}")).isInstanceOf(BusinessException.class);
    }
}
