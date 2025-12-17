package com.gdin.inspection.graphrag.v2.index.prompts;

public class SummarizeDescriptionPromptsZh {

    public static final String SUMMARIZE_PROMPT = """
你是一名有用的助手，负责对下方提供的数据生成一份全面的摘要。
给定一个或多个实体，以及一组描述列表，这些描述都与同一个实体或同一组实体相关。
请将这些描述整合为一段单一、全面的综合描述，确保覆盖描述列表中的全部信息来源。
如果提供的描述之间存在矛盾，请消解这些矛盾，并输出一段单一且连贯的总结。
请确保使用第三人称撰写，并在总结中包含实体名称，以便读者获得完整上下文。
最终描述长度限制为 {max_length} 个词。

#######
-数据-
实体: {entity_name}
描述列表: {description_list}
#######
输出:""";
}
