package org.example.rag.intent;

import lombok.Getter;

/**
 * 意图枚举
 */
@Getter
public enum IntentEnum {
    KNOWLEDGE("知识查询", "用户询问知识库中的概念、原理、定义等问题"),
    CODE("代码生成", "用户要求生成、编写、调试代码或提供编程解决方案"),
    REALTIME("实时数据", "用户询问当前时间、实时指标、在线状态等需要即时数据的问题"),
    SYSTEM("系统操作", "用户要求执行系统管理、配置更改、运维操作等"),
    CHITCHAT("闲聊", "用户进行日常寒暄、问候或无明确目的的对话");

    private final String label;
    private final String description;

    IntentEnum(String label, String description) {
        this.label = label;
        this.description = description;
    }
}