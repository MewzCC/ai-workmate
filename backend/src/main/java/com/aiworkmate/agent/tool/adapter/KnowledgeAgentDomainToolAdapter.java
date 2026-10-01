package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.service.KnowledgeBaseService;
import com.aiworkmate.service.KnowledgeService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class KnowledgeAgentDomainToolAdapter implements KnowledgeToolPort {
    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public KnowledgeToolPort.Result search(ToolActorContext context, KnowledgeToolPort.Query query) {
        var response = knowledgeService.search(context.userId(),
                new KnowledgeSearchRequest(query.text(), query.topK(), query.minScore()));
        return new KnowledgeToolPort.Result(response.records().stream().map(item -> new KnowledgeToolPort.Item(
                item.content(), item.score(), item.matchType(), item.docId(), item.chunkId(),
                item.filename(), item.chunkIndex())).toList());
    }

    @Override
    public CreateBaseResult createBase(ToolActorContext context, CreateBaseCommand command) {
        var response = knowledgeBaseService.createAgent(context.userId(),
                new KnowledgeBaseCreateRequest(command.name(), command.icon(), command.description()));
        return new CreateBaseResult(response.id(), response.name(), response.icon(), response.description(),
                response.docCount(), response.chunkCount(), response.createdAt());
    }

    @Override
    public CreateTextResult createText(ToolActorContext context, CreateTextCommand command) {
        var response = knowledgeService.createAgent(context.userId(),
                new KnowledgeDocumentCreateRequest(command.kbId(), command.filename(), command.content()));
        return new CreateTextResult(response.id(), command.kbId(), response.filename(), response.status(),
                response.chunkCount(), response.createdAt());
    }
}
