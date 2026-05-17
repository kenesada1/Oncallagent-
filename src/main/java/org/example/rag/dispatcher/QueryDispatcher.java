package org.example.rag.dispatcher;

import org.example.rag.intent.IntentRouter;
import org.example.rag.intent.RouteResult;
import org.example.service.HybridSearchService;
import org.example.service.RagService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询分发器
 * 根据意图路由到不同处理链路
 */
@Service
public class QueryDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(QueryDispatcher.class);

    private static final String MSG_REALTIME = "实时数据功能开发中，请稍后再试~";
    private static final String MSG_SYSTEM = "系统操作功能开发中，请联系管理员进行操作~";
    private static final String MSG_CHITCHAT = "你好呀！有什么我可以帮您的吗？";

    private static final Map<String, String> CHITCHAT_RESPONSES = new HashMap<>();

    static {
        CHITCHAT_RESPONSES.put("你好", "你好呀！有什么我可以帮您的吗？");
        CHITCHAT_RESPONSES.put("嗨", "嗨！很高兴见到您！");
        CHITCHAT_RESPONSES.put("Hi", "Hi！有什么问题我可以帮您解答吗？");
        CHITCHAT_RESPONSES.put("hi", "hi！有什么我可以帮您的吗？");
        CHITCHAT_RESPONSES.put("再见", "再见！祝您生活愉快！");
        CHITCHAT_RESPONSES.put("谢谢", "不客气！很高兴能帮到您~");
    }

    @Autowired
    private IntentRouter intentRouter;

    @Autowired
    private RagService ragService;

    @Autowired
    private HybridSearchService hybridSearchService;

    /**
     * 分发查询
     */
    public String dispatch(String query) {
        RouteResult routeResult = intentRouter.route(query);
        logger.info("查询分发: query={}, intent={}, confidence={}, uncertain={}",
                query, routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());

        switch (routeResult.getIntent()) {
            case KNOWLEDGE:
                return handleKnowledge(query, routeResult);
            case CODE:
                return handleCode(query, routeResult);
            case REALTIME:
                return handleRealtime(query, routeResult);
            case SYSTEM:
                return handleSystem(query, routeResult);
            case CHITCHAT:
                return handleChitchat(query, routeResult);
            default:
                return handleKnowledge(query, routeResult);
        }
    }

    /**
     * 流式分发查询
     */
    public void dispatchStream(String query, RagService.StreamCallback callback) {
        RouteResult routeResult = intentRouter.route(query);
        logger.info("流式查询分发: query={}, intent={}, confidence={}, uncertain={}",
                query, routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());

        switch (routeResult.getIntent()) {
            case KNOWLEDGE:
                ragService.queryStream(query, callback);
                break;
            case CODE:
                callback.onComplete(MSG_CHITCHAT, "");
                break;
            case REALTIME:
            case SYSTEM:
            case CHITCHAT:
                callback.onComplete(MSG_CHITCHAT, "");
                break;
            default:
                ragService.queryStream(query, callback);
        }
    }

    private String handleKnowledge(String query, RouteResult routeResult) {
        logger.info("路由到 KNOWLEDGE 链路（完整 RAG）intent={} confidence={} uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());

        List<VectorSearchService.SearchResult> searchResults = hybridSearchService.hybridSearch(query, 10);
        if (searchResults.isEmpty()) {
            logger.info("知识库检索结果为空，未匹配到相关文档");
        } else {
            logger.info("知识库检索到 {} 条相关文档", searchResults.size());
        }
        return "[KNOWLEDGE] 正在检索知识库...";
    }

    private String handleCode(String query, RouteResult routeResult) {
        logger.info("路由到 CODE 链路（直接 LLM 生成）intent={} confidence={} uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());
        return "[CODE] 代码生成模式，请使用专用接口";
    }

    private String handleRealtime(String query, RouteResult routeResult) {
        logger.info("路由到 REALTIME 链路 intent={} confidence={} uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());
        return MSG_REALTIME;
    }

    private String handleSystem(String query, RouteResult routeResult) {
        logger.info("路由到 SYSTEM 链路 intent={} confidence={} uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());
        return MSG_SYSTEM;
    }

    private String handleChitchat(String query, RouteResult routeResult) {
        logger.info("路由到 CHITCHAT 链路 intent={} confidence={} uncertain={}",
                routeResult.getIntent(), routeResult.getConfidence(), routeResult.isUncertain());
        for (Map.Entry<String, String> entry : CHITCHAT_RESPONSES.entrySet()) {
            if (query.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return MSG_CHITCHAT;
    }
}