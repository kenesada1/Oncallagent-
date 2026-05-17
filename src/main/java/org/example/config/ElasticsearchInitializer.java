package org.example.config;

import jakarta.annotation.PostConstruct;
import org.example.entity.ChunkDocument;
import org.example.repository.ChunkDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 初始化器
 * 检查 ES 连接和索引状态，自动创建索引（如不存在）
 */
@Component
public class ElasticsearchInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchInitializer.class);

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ChunkDocumentRepository chunkDocumentRepository;

    @PostConstruct
    public void init() {
        try {
            logger.info("检查 Elasticsearch 连接状态...");

            // 检查索引是否存在
            boolean indexExists = elasticsearchOperations.indexOps(ChunkDocument.class).exists();
            logger.info("索引 'doc_chunks' 存在状态: {}", indexExists);

            if (!indexExists) {
                logger.info("索引不存在，正在创建 'doc_chunks'...");
                elasticsearchOperations.indexOps(ChunkDocument.class).create();
                elasticsearchOperations.indexOps(ChunkDocument.class).putMapping();
                logger.info("✓ 索引 'doc_chunks' 创建成功");
            } else {
                logger.info("✓ Elasticsearch 连接正常，索引已存在");
            }

        } catch (Exception e) {
            logger.error("Elasticsearch 初始化失败: {}", e.getMessage());
            logger.warn("ES 不可用时，系统将降级为纯向量检索模式");
        }
    }
}