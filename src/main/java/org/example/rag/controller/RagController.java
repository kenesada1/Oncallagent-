package org.example.rag.controller;

import org.example.rag.dispatcher.QueryDispatcher;
import org.example.rag.intent.IntentEnum;
import org.example.rag.intent.IntentRouter;
import org.example.rag.intent.RouteResult;
import org.example.service.RagService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 查询控制器
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger logger = LoggerFactory.getLogger(RagController.class);

    @Autowired
    private QueryDispatcher queryDispatcher;

    @Autowired
    private IntentRouter intentRouter;

    @Autowired
    private RagService ragService;

    /**
     * 意图路由接口
     */
    @PostMapping("/intent")
    public Map<String, Object> detectIntent(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        logger.info("意图检测请求: query={}", query);

        RouteResult result = intentRouter.route(query);

        Map<String, Object> response = new HashMap<>();
        response.put("intent", result.getIntent().name());
        response.put("label", result.getIntent().getLabel());
        response.put("confidence", result.getConfidence());
        response.put("uncertain", result.isUncertain());

        return response;
    }

    /**
     * 查询接口（同步，完整 RAG 流程）
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        logger.info("查询请求: query={}", query);

        RouteResult routeResult = intentRouter.route(query);
        logger.info("路由结果: intent={}, confidence={}, uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("intent", routeResult.getIntent().name());
        response.put("confidence", routeResult.getConfidence());
        response.put("uncertain", routeResult.isUncertain());

        // 非 KNOWLEDGE 和非 UNCERTAIN 的情况直接返回
        if (routeResult.getIntent() != IntentEnum.KNOWLEDGE && !routeResult.isUncertain()) {
            switch (routeResult.getIntent()) {
                case CODE:
                    response.put("mode", "CODE");
                    response.put("message", "代码生成模式，请使用专用接口");
                    response.put("status", "redirect");
                    break;
                case REALTIME:
                    response.put("mode", "REALTIME");
                    response.put("message", "实时数据功能开发中");
                    response.put("status", "unavailable");
                    break;
                case SYSTEM:
                    response.put("mode", "SYSTEM");
                    response.put("message", "系统操作功能开发中");
                    response.put("status", "unavailable");
                    break;
                case CHITCHAT:
                    response.put("mode", "CHITCHAT");
                    response.put("message", "你好呀！有什么我可以帮您的吗？");
                    response.put("status", "success");
                    break;
                default:
                    break;
            }
            return response;
        }

        // KNOWLEDGE / UNCERTAIN：执行完整 RAG 流程
        try {
            logger.info("执行 RAG 完整流程...");
            StringBuilder fullAnswer = new StringBuilder();

            SyncStreamCallback callback = new SyncStreamCallback(fullAnswer);
            ragService.queryStream(query, callback);

            // 等待回调完成
            synchronized (callback.lock) {
                while (!callback.completed && callback.error == null) {
                    try {
                        callback.lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (callback.error != null) {
                logger.error("RAG 执行失败", callback.error);
                response.put("mode", "RAG");
                response.put("message", "RAG 执行失败: " + callback.error.getMessage());
                response.put("status", "error");
            } else {
                logger.info("RAG 执行完成，答案长度: {}", fullAnswer.length());
                response.put("mode", "RAG");
                response.put("answer", fullAnswer.toString());
                response.put("searchResults", callback.searchResults);
                response.put("status", "success");
            }

        } catch (Exception e) {
            logger.error("RAG 调用异常", e);
            response.put("mode", "RAG");
            response.put("message", "RAG 调用异常: " + e.getMessage());
            response.put("status", "error");
        }

        return response;
    }

    /**
     * 同步流式回调
     */
    private static class SyncStreamCallback implements RagService.StreamCallback {
        final StringBuilder fullAnswer;
        final Object lock = new Object();
        boolean completed = false;
        Exception error = null;
        List<VectorSearchService.SearchResult> searchResults = new ArrayList<>();

        SyncStreamCallback(StringBuilder fullAnswer) {
            this.fullAnswer = fullAnswer;
        }

        @Override
        public void onSearchResults(List<VectorSearchService.SearchResult> results) {
            this.searchResults = results;
            logger.info("RAG 检索到 {} 条结果", results.size());
        }

        @Override
        public void onReasoningChunk(String chunk) {
            // 推理过程不追加到答案
        }

        @Override
        public void onContentChunk(String chunk) {
            fullAnswer.append(chunk);
        }

        @Override
        public void onComplete(String fullContent, String fullReasoning) {
            synchronized (lock) {
                completed = true;
                lock.notifyAll();
            }
        }

        @Override
        public void onError(Exception e) {
            synchronized (lock) {
                error = e;
                lock.notifyAll();
            }
        }
    }

    /**
     * 流式查询接口
     */
    @PostMapping("/query/stream")
    public Map<String, Object> queryStream(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        logger.info("流式查询请求: query={}", query);

        RouteResult routeResult = intentRouter.route(query);

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("intent", routeResult.getIntent().name());
        response.put("uncertain", routeResult.isUncertain());

        if (routeResult.getIntent() == IntentEnum.KNOWLEDGE || routeResult.isUncertain()) {
            response.put("mode", "stream");
            response.put("status", "ok");
        } else {
            response.put("mode", "sync");
            response.put("status", "ok");
        }

        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "RagController");
        return response;
    }
}