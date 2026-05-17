package org.example.service;

import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 结合向量检索和 BM25 关键词检索，使用 RRF 融合
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    private static final int RRF_K = 60; // RRF 平滑常数

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private KeywordSearchService keywordSearchService;

    @Value("${hybrid.candidate-k:10}")
    private int candidateK;

    /**
     * 混合搜索
     *
     * @param query 查询文本
     * @param topK 返回最终结果数量（用于最终截取）
     * @return 融合后的搜索结果（返回所有 RRF 候选，供重排使用）
     */
    public List<SearchResult> hybridSearch(String query, int topK) {
        try {
            logger.info("开始混合检索，查询: {}, topK: {}, candidateK: {}", query, topK, candidateK);

            // 1. 向量检索（语义）
            List<SearchResult> vectorResults = vectorSearchService.searchSimilarDocuments(query, candidateK);
            logger.info("向量检索返回 {} 个结果", vectorResults.size());

            // 2. BM25 关键词检索
            List<SearchResult> keywordResults = keywordSearchService.searchByKeyword(query, candidateK);
            logger.info("BM25 检索返回 {} 个结果", keywordResults.size());

            // 3. RRF 融合（返回所有候选，不截取）
            List<SearchResult> fusedResults = rrfFusion(vectorResults, keywordResults);
            logger.info("RRF 融合完成，返回 {} 个结果（所有候选）", fusedResults.size());

            return fusedResults;

        } catch (Exception e) {
            logger.error("混合检索失败", e);
            // 降级：返回纯向量检索结果
            return vectorSearchService.searchSimilarDocuments(query, topK);
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合
     * 公式: score = Σ(1.0 / (k + rank))
     * 返回所有 RRF 候选结果，不截取
     *
     * @param vectorResults 向量检索结果
     * @param keywordResults BM25 检索结果
     * @return 融合后的所有结果
     */
    private List<SearchResult> rrfFusion(List<SearchResult> vectorResults,
                                          List<SearchResult> keywordResults) {
        // 构建 id -> SearchResult 的映射，量纲归一后的 RRF 分数
        Map<String, SearchResult> resultMap = new HashMap<>();

        // 添加向量检索结果，按排名赋予 RRF 分数（替换原始分数，量纲归一）
        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult result = vectorResults.get(i);
            result.setScore(1.0f / (RRF_K + i + 1));
            resultMap.put(result.getId(), result);
        }

        // 添加 BM25 检索结果，累加 RRF 分数
        for (int i = 0; i < keywordResults.size(); i++) {
            SearchResult result = keywordResults.get(i);
            if (resultMap.containsKey(result.getId())) {
                // 已存在，累加 RRF 分数
                SearchResult existing = resultMap.get(result.getId());
                existing.setScore(existing.getScore() + (1.0f / (RRF_K + i + 1)));
            } else {
                // 新增，初始化为 RRF 分数
                SearchResult newResult = new SearchResult();
                newResult.setId(result.getId());
                newResult.setContent(result.getContent());
                newResult.setMetadata(result.getMetadata());
                newResult.setScore(1.0f / (RRF_K + i + 1));
                resultMap.put(result.getId(), newResult);
            }
        }

        // 按 RRF 分数排序
        List<SearchResult> sortedResults = resultMap.values().stream()
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());

        // 不截取，返回所有 RRF 候选结果
        return sortedResults;
    }

    /**
     * 单独获取向量检索结果（用于调试）
     */
    public List<SearchResult> getVectorResults(String query) {
        return vectorSearchService.searchSimilarDocuments(query, candidateK);
    }

    /**
     * 单独获取 BM25 检索结果（用于调试）
     */
    public List<SearchResult> getKeywordResults(String query) {
        return keywordSearchService.searchByKeyword(query, candidateK);
    }
}