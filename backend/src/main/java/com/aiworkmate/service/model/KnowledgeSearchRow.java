package com.aiworkmate.service.model;

import lombok.Data;

@Data
public class KnowledgeSearchRow {

    private Long chunkId;
    private Long docId;
    private String filename;
    private Integer chunkIndex;
    private String content;
    private Double score;
}
