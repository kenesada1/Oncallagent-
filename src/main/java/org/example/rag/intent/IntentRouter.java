package org.example.rag.intent;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 意图识别服务
 * 使用向量相似度匹配用户查询与预定义意图
 */
@Service
public class IntentRouter {

    private static final Logger logger = LoggerFactory.getLogger(IntentRouter.class);

    @Autowired
    private org.example.service.VectorEmbeddingService vectorEmbeddingService;

    @Value("${rag.intent.threshold:0.6}")
    private double threshold;

    @Value("${rag.intent.descriptions.knowledge:用户询问知识库中的概念、原理、定义、故障排查、问题诊断及运维相关问题，包括服务器无法连接、CPU使用率高、磁盘空间不足、内存泄漏、Nginx异常、数据库连接问题、Docker容器问题、日志分析方法、备份恢复操作、性能监控命令、安全检查等运维故障诊断和问题解答}")
    private String knowledgeDesc;

    @Value("${rag.intent.descriptions.code:用户要求生成、编写、调试代码或提供编程解决方案")
    private String codeDesc;

    @Value("${rag.intent.descriptions.realtime:用户询问当前时间、实时指标、在线状态等需要即时数据的问题")
    private String realtimeDesc;

    @Value("${rag.intent.descriptions.system:用户要求执行系统管理、配置更改、运维操作等")
    private String systemDesc;

    @Value("${rag.intent.descriptions.chitchat:用户进行日常寒暄、问候或无明确目的的对话")
    private String chitchatDesc;

    @Getter
    private final Map<IntentEnum, List<Float>> intentVectors = new EnumMap<>(IntentEnum.class);

    @PostConstruct
    public void init() {
        logger.info("开始预计算意图向量，阈值: {}", threshold);
        try {
            intentVectors.put(IntentEnum.KNOWLEDGE, vectorEmbeddingService.generateEmbedding(knowledgeDesc));
            intentVectors.put(IntentEnum.CODE, vectorEmbeddingService.generateEmbedding(codeDesc));
            intentVectors.put(IntentEnum.REALTIME, vectorEmbeddingService.generateEmbedding(realtimeDesc));
            intentVectors.put(IntentEnum.SYSTEM, vectorEmbeddingService.generateEmbedding(systemDesc));
            intentVectors.put(IntentEnum.CHITCHAT, vectorEmbeddingService.generateEmbedding(chitchatDesc));

            logger.info("意图向量预计算完成，共 {} 个意图", intentVectors.size());
        } catch (Exception e) {
            logger.error("意图向量预计算失败，将使用默认逻辑", e);
        }
    }

    /**
     * 路由用户查询
     *
     * @param query 用户查询
     * @return 路由结果
     */
    public RouteResult route(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("空查询，默认为 KNOWLEDGE");
            return RouteResult.uncertain();
        }

        try {
            List<Float> queryVector = vectorEmbeddingService.generateEmbedding(query.trim());

            IntentEnum bestIntent = IntentEnum.KNOWLEDGE;
            double bestSimilarity = -1;

            for (Map.Entry<IntentEnum, List<Float>> entry : intentVectors.entrySet()) {
                double similarity = vectorEmbeddingService.calculateCosineSimilarity(queryVector, entry.getValue());
                logger.debug("意图 {} 相似度: {}", entry.getKey(), similarity);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestIntent = entry.getKey();
                }
            }

            logger.info("路由结果: intent={}, confidence={}, threshold={}",
                    bestIntent, bestSimilarity, threshold);

            if (bestSimilarity < threshold) {
                // KNOWLEDGE 即使置信度低也让 RAG 兜底，其他意图走 LLM
                IntentEnum fallbackIntent = (bestIntent == IntentEnum.KNOWLEDGE)
                        ? IntentEnum.KNOWLEDGE
                        : IntentEnum.CHITCHAT;
                return RouteResult.builder()
                        .intent(fallbackIntent)
                        .confidence(bestSimilarity)
                        .uncertain(true)
                        .build();
            }

            return RouteResult.of(bestIntent, bestSimilarity);

        } catch (Exception e) {
            logger.error("意图识别异常，默认走 KNOWLEDGE", e);
            return RouteResult.uncertain();
        }
    }
}