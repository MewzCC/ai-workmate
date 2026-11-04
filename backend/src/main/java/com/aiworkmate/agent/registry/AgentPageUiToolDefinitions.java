package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentPageUiToolDefinitions {
    public static final String INPUT = """
            {"type":"object","additionalProperties":false,"required":["pageId","code"],"properties":{"pageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"code":{"type":"string","enum":["ui.navigate","ui.applyFilter","ui.openDetail","ui.openCreateForm","ui.fillForm","ui.previewSubmission","ui.refreshPage"]},"targetPageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"recordId":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"}]},"fields":{"type":"array","maxItems":32,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,39}$"},"value":{"oneOf":[{"type":"string","maxLength":500},{"type":"number","minimum":-1000000000000,"maximum":1000000000000},{"type":"boolean"}]}}}}}}
            """.strip();
    public static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["pageId","code","fields"],"properties":{"pageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"code":{"type":"string","enum":["ui.navigate","ui.applyFilter","ui.openDetail","ui.openCreateForm","ui.fillForm","ui.previewSubmission","ui.refreshPage"]},"targetPageId":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,59}$"},"recordId":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"}]},"fields":{"type":"array","maxItems":32,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,39}$"},"value":{"oneOf":[{"type":"string","maxLength":500},{"type":"number","minimum":-1000000000000,"maximum":1000000000000},{"type":"boolean"}]}}}},"formContext":{"type":"object","additionalProperties":false,"required":["sessionId"],"properties":{"sessionId":{"type":"string","pattern":"^[a-zA-Z0-9_-]{16,64}$"},"recordId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}},"oneOf":[{"required":["recordId","version"]},{"not":{"anyOf":[{"required":["recordId"]},{"required":["version"]}]}}]}}}
            """.strip();

    @Bean
    ToolDefinition pageUiPrepare(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.PAGE_UI_PREPARE, "Prepare a page interaction",
                "Prepares one authorized browser navigation, filter, detail, unsaved form or refresh command.",
                "UI state only. Never submits, approves, deletes or persists business data. Browser validates current permissions and handlers. Use fields only for filter or fill; recordId only for detail. Before acting across a multi-view page, prepare an applyFilter with field view: my-applications leave/generic, dictionary types/items, knowledge-base list/overview/docs/query/settings. Select only an available view; subsequent steps stay bound to it. Filling requires a current form session or an earlier openCreateForm in this task.",
                mapper.readTree(INPUT), mapper.readTree(OUTPUT), Set.of("agent:ui:prepare"),
                OwnershipPolicy.SELF, 32, 32768, 15000);
    }
}
