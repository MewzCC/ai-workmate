package com.aiworkmate.service;

import java.util.List;

/**
 * 检索结果重排服务。在向量/全文检索召回后，用 rerank 模型对候选重新打分排序，
 * 提升「目录页、低相关片段」等噪声的过滤效果。
 */
public interface RerankService {

    /** 是否已启用且密钥/模型配置完整 */
    boolean configured();

    /**
     * 对候选文档重排。
     *
     * @param query     用户查询
     * @param documents 候选文档内容（顺序与检索结果一致）
     * @param topN      最多返回多少条
     * @return 按相关性降序的（原始索引, 重排分数）列表
     */
    List<RankedItem> rerank(String query, List<String> documents, int topN);

    record RankedItem(int index, double score) {
    }
}
