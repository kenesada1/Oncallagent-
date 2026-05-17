package org.example.controller;

import org.example.service.DashScopeRerankService;
import org.example.service.HybridSearchService;
import org.example.service.KeywordSearchService;
import org.example.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索测试接口
 * 用于验证双路召回、RRF融合和重排效果
 */
@RestController
@RequestMapping("/api/test")
public class SearchTestController {

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private KeywordSearchService keywordSearchService;

    @Autowired
    private DashScopeRerankService dashScopeRerankService;

    /**
     * 测试混合检索全流程
     * 返回各阶段的详细信息，方便理解每一步做了什么
     *
     * @param query 查询关键词
     * @param topK 最终返回数量
     * @param candidateK 每路召回数量
     */
    @GetMapping("/search")
    public Map<String, Object> testSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(defaultValue = "50") int candidateK) {

        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // 阶段1: 向量检索（语义搜索）
            long t1 = System.currentTimeMillis();
            List<VectorSearchService.SearchResult> vectorResults =
                vectorSearchService.searchSimilarDocuments(query, candidateK);
            long t2 = System.currentTimeMillis();

            // 阶段2: BM25 检索（关键词搜索）
            long t3 = System.currentTimeMillis();
            List<VectorSearchService.SearchResult> bm25Results =
                keywordSearchService.searchByKeyword(query, candidateK);
            long t4 = System.currentTimeMillis();

            // 阶段3: RRF 融合
            long t5 = System.currentTimeMillis();
            List<VectorSearchService.SearchResult> fusedResults =
                hybridSearchService.hybridSearch(query, topK);
            long t6 = System.currentTimeMillis();

            // 阶段4: 重排模型精排
            long t7 = System.currentTimeMillis();
            List<VectorSearchService.SearchResult> rerankedResults =
                dashScopeRerankService.rerank(query, fusedResults);
            long t8 = System.currentTimeMillis();

            // 截取最终 topK 条
            if (rerankedResults.size() > topK) {
                rerankedResults = rerankedResults.subList(0, topK);
            }

            // ========== 组装结果 ==========
            result.put("success", true);
            result.put("query", query);
            result.put("params", Map.of(
                "topK", topK,
                "candidateK", candidateK
            ));

            // 各阶段详细说明
            Map<String, Object> stages = new HashMap<>();

            // 阶段1: 向量检索
            Map<String, Object> vectorStage = new HashMap<>();
            vectorStage.put("name", "向量检索（语义搜索）");
            vectorStage.put("description", "使用 Milvus 向量数据库，通过 embedding 模型将查询和文档都转为向量，计算余弦相似度返回结果");
            vectorStage.put("召回数量", vectorResults.size());
            vectorStage.put("耗时ms", t2 - t1);
            vectorStage.put("top3结果", getTop3WithScore(vectorResults));
            stages.put("step1_vectorSearch", vectorStage);

            // 阶段2: BM25 检索
            Map<String, Object> bm25Stage = new HashMap<>();
            bm25Stage.put("name", "BM25 检索（关键词搜索）");
            bm25Stage.put("description", "使用 Elasticsearch，对查询和文档进行分词，计算词频和逆文档频率，返回关键词匹配结果");
            bm25Stage.put("召回数量", bm25Results.size());
            bm25Stage.put("耗时ms", t4 - t3);
            bm25Stage.put("top3结果", getTop3WithScore(bm25Results));
            stages.put("step2_bm25Search", bm25Stage);

            // 阶段3: RRF 融合
            Map<String, Object> rrfStage = new HashMap<>();
            rrfStage.put("name", "RRF 融合（排名融合）");
            rrfStage.put("description", "使用 Reciprocal Rank Fusion 算法，将向量检索和 BM25 的结果按排名融合，公式: score = Σ(1/(k+rank))");
            rrfStage.put("输入数量", vectorResults.size() + bm25Results.size());
            rrfStage.put("输出数量", fusedResults.size());
            rrfStage.put("耗时ms", t6 - t5);
            rrfStage.put("top3结果", getTop3WithScore(fusedResults));
            stages.put("step3_rrfFusion", rrfStage);

            // 阶段4: 重排模型
            Map<String, Object> rerankStage = new HashMap<>();
            rerankStage.put("name", "重排模型（语义精排）");
            rerankStage.put("description", "使用 DashScope qwen3-rerank 模型，对查询和文档进行语义配对分析，重新计算相关性分数并排序");
            rerankStage.put("输入数量", fusedResults.size());
            rerankStage.put("输出数量", rerankedResults.size());
            rerankStage.put("耗时ms", t8 - t7);
            rerankStage.put("top3结果", getTop3WithScore(rerankedResults));
            stages.put("step4_rerank", rerankStage);

            result.put("stages", stages);
            result.put("totalTimeMs", t8 - startTime);
            result.put("finalCount", rerankedResults.size());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 测试纯向量检索
     */
    @GetMapping("/search/vector")
    public Map<String, Object> testVectorSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {

        long startTime = System.currentTimeMillis();
        List<VectorSearchService.SearchResult> results =
            vectorSearchService.searchSimilarDocuments(query, topK);

        Map<String, Object> result = new HashMap<>();
        result.put("method", "纯向量检索（语义搜索）");
        result.put("description", "使用 Milvus 向量数据库，通过 embedding 模型将查询转为向量，计算余弦相似度返回结果");
        result.put("success", true);
        result.put("query", query);
        result.put("topK", topK);
        result.put("count", results.size());
        result.put("timeMs", System.currentTimeMillis() - startTime);
        result.put("results", getTop3WithScore(results));

        return result;
    }

    /**
     * 测试纯 BM25 检索
     */
    @GetMapping("/search/bm25")
    public Map<String, Object> testBm25Search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {

        long startTime = System.currentTimeMillis();
        List<VectorSearchService.SearchResult> results =
            keywordSearchService.searchByKeyword(query, topK);

        Map<String, Object> result = new HashMap<>();
        result.put("method", "纯 BM25 检索（关键词搜索）");
        result.put("description", "使用 Elasticsearch，对查询和文档进行分词，计算词频和逆文档频率，返回关键词匹配结果");
        result.put("success", true);
        result.put("query", query);
        result.put("topK", topK);
        result.put("count", results.size());
        result.put("timeMs", System.currentTimeMillis() - startTime);
        result.put("results", getTop3WithScore(results));

        return result;
    }

    /**
     * 测试 RRF 融合（不经过重排）
     */
    @GetMapping("/search/rrf")
    public Map<String, Object> testRrfFusion(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "50") int candidateK) {

        long startTime = System.currentTimeMillis();
        List<VectorSearchService.SearchResult> results =
            hybridSearchService.hybridSearch(query, topK);
        long endTime = System.currentTimeMillis();

        Map<String, Object> result = new HashMap<>();
        result.put("method", "RRF 融合（排名融合）");
        result.put("description", "使用 Reciprocal Rank Fusion 算法，将向量检索和 BM25 的结果按排名融合，公式: score = Σ(1/(k+rank))");
        result.put("success", true);
        result.put("query", query);
        result.put("params", Map.of("topK", topK, "candidateK", candidateK));
        result.put("count", results.size());
        result.put("timeMs", endTime - startTime);
        result.put("results", getTop3WithScore(results));

        return result;
    }

    // ========== 工具方法 ==========

    private List<Map<String, Object>> getTop3(List<VectorSearchService.SearchResult> results) {
        return getTop3WithScore(results);
    }

    private List<Map<String, Object>> getTop3WithScore(List<VectorSearchService.SearchResult> results) {
        int limit = Math.min(3, results.size());
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (int i = 0; i < limit; i++) {
            VectorSearchService.SearchResult r = results.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("id", r.getId());
            item.put("score", r.getScore());
            // 截取内容前100字符
            String content = r.getContent();
            item.put("content", content != null && content.length() > 100
                ? content.substring(0, 100) + "..." : content);
            list.add(item);
        }
        return list;
    }
}