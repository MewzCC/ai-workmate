package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeAgentDomainToolAdapter implements KnowledgeToolPort {
    private final KnowledgeService knowledgeService;

    @Override
    public KnowledgeToolPort.Result search(ToolActorContext context, KnowledgeToolPort.Query query) {
        var response = knowledgeService.search(context.userId(),
                new KnowledgeSearchRequest(query.text(), query.topK(), query.minScore()));
        return new KnowledgeToolPort.Result(response.records().stream().map(item -> new KnowledgeToolPort.Item(
                item.content(), item.score(), item.matchType(), item.docId(), item.chunkId(),
                item.filename(), item.chunkIndex())).toList());
    }
}
