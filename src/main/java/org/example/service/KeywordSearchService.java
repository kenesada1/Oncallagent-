package org.example.service;

import org.example.entity.ChunkDocument;
import org.example.repository.ChunkDocumentRepository;
import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词检索服务
 * 使用 Elasticsearch BM25 进行关键词搜索
 */
@Service
public class KeywordSearchService {

    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchService.class);

    @Autowired
    private ChunkDocumentRepository chunkDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * 使用 BM25 进行关键词搜索
     *
     * @param query 搜索关键词
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> searchByKeyword(String query, int topK) {
        try {
            logger.info("开始 BM25 关键词搜索，关键词: {}, topK: {}", query, topK);

            // 构建 Criteria 查询（走 BM25）
            Criteria criteria = new Criteria("content").matches(query);
            CriteriaQuery criteriaQuery = new CriteriaQuery(criteria);
            criteriaQuery.setPageable(PageRequest.of(0, topK));

            SearchHits<ChunkDocument> searchHits = elasticsearchOperations.search(
                criteriaQuery, ChunkDocument.class
            );

            List<SearchResult> results = new ArrayList<>();
            for (SearchHit<ChunkDocument> hit : searchHits) {
                ChunkDocument doc = hit.getContent();
                SearchResult result = new SearchResult();
                result.setId(doc.getId());
                result.setContent(doc.getContent());
                result.setScore((float) hit.getScore());
                result.setMetadata(doc.getMetadata());
                results.add(result);
            }

            logger.info("BM25 搜索完成，返回 {} 个结果", results.size());
            return results;

        } catch (Exception e) {
            logger.error("BM25 关键词搜索失败", e);
            return new ArrayList<>();
        }
    }

    

    /**
     * 保存文档到 Elasticsearch
     */
    public void saveDocument(ChunkDocument document) {
        try {
            chunkDocumentRepository.save(document);
            logger.debug("文档已保存到 ES: id={}", document.getId());
        } catch (Exception e) {
            throw new RuntimeException("保存文档到 ES 失败: id=" + document.getId(), e);
        }
    }

    /**
     * 根据 source 删除文档
     */
    public void deleteBySource(String source) {
        try {
            chunkDocumentRepository.deleteBySource(source);
            logger.info("已从 ES 删除 source={} 的所有文档", source);
        } catch (Exception e) {
            logger.error("从 ES 删除文档失败: source={}", source, e);
        }
    }

    /**
     * 将 DocumentChunk 转换为 ChunkDocument
     */
    public ChunkDocument fromDocumentChunk(org.example.dto.DocumentChunk chunk, String source, String metadata) {
        String id = java.util.UUID.nameUUIDFromBytes((source + "_" + chunk.getChunkIndex()).getBytes()).toString();
        ChunkDocument doc = new ChunkDocument();
        doc.setId(id);
        doc.setContent(chunk.getContent());
        doc.setSource(source);
        doc.setChunkIndex(chunk.getChunkIndex());
        doc.setTitle(chunk.getTitle());
        doc.setMetadata(metadata);
        return doc;
    }
}