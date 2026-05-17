package org.example.repository;

import org.example.entity.ChunkDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.elasticsearch.annotations.Query;

import java.util.List;

/**
 * Elasticsearch 文档仓库
 * 用于 BM25 关键词检索
 */
public interface ChunkDocumentRepository extends ElasticsearchRepository<ChunkDocument, String> {

    /**
     * 根据内容关键词搜索
     */
    @Query("{\"match\": {\"content\": {\"query\": \"?0\"}}}")
    List<ChunkDocument> findByContentContaining(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据 source 删除所有文档
     */
    void deleteBySource(String source);

    /**
     * 根据 source 查找所有文档
     */
    List<ChunkDocument> findBySource(String source);
}