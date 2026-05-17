package org.example.service;

import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 阿里云 DashScope Rerank 服务
 * 用于对检索结果进行语义级别的二次排序
 */
@Service
public class DashScopeRerankService {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeRerankService.class);

    private static final String DEFAULT_INSTRUCT = "Given a web search query, retrieve relevant passages that answer the query.";

    @Value("${dashscope.api.key:}")
    private String apiKey;

    @Value("${dashscope.rerank.model:qwen3-rerank}")
    private String rerankModel;

    @Value("${dashscope.rerank.top-n:3}")
    private int topN;

    @Autowired
    private RestClient.Builder restClientBuilder;

    /**
     * 对检索结果进行重排
     *
     * @param query         查询文本
     * @param searchResults  原始检索结果（来自向量检索）
     * @return 重排后的结果列表
     */
    public List<SearchResult> rerank(String query, List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            logger.warn("检索结果为空，跳过重排");
            return searchResults;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("未配置 DashScope Rerank API Key，直接返回原始检索结果");
            return searchResults;
        }

        try {
            logger.info("开始调用 DashScope Rerank API，模型: {}, 待重排文档数: {}", rerankModel, searchResults.size());

            // 构建请求体
            RerankRequest request = new RerankRequest();
            request.setModel(rerankModel);
            request.setQuery(query);
            request.setDocuments(extractContents(searchResults));
            request.setTopN(topN);
            request.setInstruct(DEFAULT_INSTRUCT);

            // 调用重排 API
            RerankResponse response = callRerankApi(request);

            // 解析响应并重新排序
            List<SearchResult> rerankedResults = reorderResults(response, searchResults);

            logger.info("Rerank 完成，返回 {} 个结果", rerankedResults.size());
            return rerankedResults;

        } catch (Exception e) {
            logger.error("Rerank 调用失败，返回原始检索结果", e);
            return searchResults;
        }
    }

    /**
     * 提取文档内容列表
     */
    private List<String> extractContents(List<SearchResult> searchResults) {
        List<String> contents = new ArrayList<>();
        for (SearchResult result : searchResults) {
            contents.add(result.getContent());
        }
        return contents;
    }

    /**
     * 调用 DashScope 重排 API
     */
    private RerankResponse callRerankApi(RerankRequest request) {
        RestClient restClient = restClientBuilder
                .baseUrl("https://dashscope.aliyuncs.com/compatible-api/v1/reranks")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ResponseEntity<RerankResponse> responseEntity = restClient.post()
                .uri("/")
                .body(request)
                .retrieve()
                .toEntity(RerankResponse.class);

        if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
            return responseEntity.getBody();
        } else {
            throw new RuntimeException("Rerank API 调用失败: " + responseEntity.getStatusCode());
        }
    }

    /**
     * 根据重排分数重新排序结果
     */
    private List<SearchResult> reorderResults(RerankResponse response, List<SearchResult> originalResults) {
        List<RerankResponse.RerankResultItem> resultList = response.getResultItems();

        if (resultList == null || resultList.isEmpty()) {
            logger.warn("Rerank 响应结果为空，返回原始结果");
            return originalResults;
        }

        // 构建 index -> result 的映射
        Map<Integer, SearchResult> indexResultMap = new HashMap<>();
        for (int i = 0; i < originalResults.size(); i++) {
            indexResultMap.put(i, originalResults.get(i));
        }

        // 按重排分数重新排序
        List<SearchResult> reranked = new ArrayList<>();
        for (RerankResponse.RerankResultItem item : resultList) {
            int index = item.getIndex();
            if (index >= 0 && index < originalResults.size()) {
                SearchResult result = indexResultMap.get(index);
                result.setScore((float) item.getRelevanceScore());
                reranked.add(result);
            }
        }

        // 用 topN 截断结果数量
        if (reranked.size() > topN) {
            reranked = reranked.subList(0, topN);
            logger.debug("Rerank 结果截断至 topN={}", topN);
        }

        return reranked;
    }

    // ========== 请求/响应内部类 ==========

    /**
     * 重排请求体（阿里云 DashScope 格式 - 扁平结构）
     */
    static class RerankRequest {
        private String model;
        private String query;
        private List<String> documents;
        @JsonProperty("top_n")
        private int topN;
        private String instruct;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public List<String> getDocuments() { return documents; }
        public void setDocuments(List<String> documents) { this.documents = documents; }
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }
        public String getInstruct() { return instruct; }
        public void setInstruct(String instruct) { this.instruct = instruct; }
    }

    /**
     * 重排响应体（阿里云 DashScope 格式 - 兼容 qwen3-rerank 扁平结构和新版 output 结构）
     */
    static class RerankResponse {
        // qwen3-rerank 直接在根级别返回 results，无 output 包装
        private List<RerankResultItem> results;
        private String requestId;

        // 兼容旧版模型（如 gte-rerank）的 output 结构
        private Output output;

        public List<RerankResultItem> getResults() { return results; }
        public void setResults(List<RerankResultItem> results) { this.results = results; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public Output getOutput() { return output; }
        public void setOutput(Output output) { this.output = output; }

        /**
         * 获取结果列表，优先取 qwen3-rerank 的扁平 results，其次取 output.results
         */
        List<RerankResultItem> getResultItems() {
            if (results != null && !results.isEmpty()) {
                return results;
            }
            if (output != null && output.getResults() != null && !output.getResults().isEmpty()) {
                return output.getResults();
            }
            return null;
        }

        static class Output {
            private List<RerankResultItem> results;

            public List<RerankResultItem> getResults() { return results; }
            public void setResults(List<RerankResultItem> results) { this.results = results; }
        }

        static class RerankResultItem {
            private int index;
            private double relevanceScore;

            public int getIndex() { return index; }
            public void setIndex(int index) { this.index = index; }
            public double getRelevanceScore() { return relevanceScore; }
            public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
        }
    }
}