package org.example.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 文档分片实体
 * 用于 BM25 关键词检索
 */
@Document(indexName = "doc_chunks")
@Getter
@Setter
public class ChunkDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Keyword)
    private String title;

    @Field(type = FieldType.Text)
    private String metadata;

    public ChunkDocument() {
    }

    public ChunkDocument(String id, String content, String source, Integer chunkIndex, String title) {
        this.id = id;
        this.content = content;
        this.source = source;
        this.chunkIndex = chunkIndex;
        this.title = title;
    }
}