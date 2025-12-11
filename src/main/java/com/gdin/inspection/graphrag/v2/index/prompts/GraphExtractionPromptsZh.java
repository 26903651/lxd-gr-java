package com.gdin.inspection.graphrag.v2.index.prompts;

/**
 * 对齐 Python 版 GRAPH_EXTRACTION_PROMPT / CONTINUE_PROMPT / LOOP_PROMPT 的中文版本。
 * - buildSystemPrompt：生成带具体实体类型与分隔符配置的 system prompt
 * - CONTINUE_PROMPT_ZH / LOOP_PROMPT_ZH：用于后续 chat 轮次的 user prompt
 */
public final class GraphExtractionPromptsZh {

    /**
     * 构造主抽取的 system prompt。
     *
     * @param entityTypes         可选实体类型列表（逗号或其它分隔形式的中文描述）
     * @param recordDelimiter     记录分隔符
     * @param tupleDelimiter      字段分隔符
     * @param completionDelimiter 结束标记
     */
    public static String buildSystemPrompt(String entityTypes,
                                           String recordDelimiter,
                                           String tupleDelimiter,
                                           String completionDelimiter) {
        String safeTypes = entityTypes == null ? "" : entityTypes;
        return """
- 任务说明 -
你将得到一段中文文本，以及一组“实体类型列表”和若干分隔符配置。
你的任务是：
1. 从文本中识别所有“不同的实体”；
2. 在这些实体之间识别所有“清晰存在的关系”。

- 实体抽取规则 -
1. 从文本中识别所有实体（人物、组织、地点、法规、制度文件、单位、岗位、人员、问题类型、事项类型等）。
2. 对每个实体，至少需要提取以下信息：
   - entity_name：实体名称。
   - entity_type：实体类型，必须从给定的实体类型集合中选择。当前实体类型集合为：%s
   - entity_description：用自然语言描述该实体在文本中的含义、职责、属性或行为。
3. 每个实体必须使用如下 tuple 形式表示：
   ("entity"%s<entity_name>%s<entity_type>%s<entity_description>)

- 关系抽取规则 -
1. 在已识别到的实体中，找出所有“存在明确关系”的实体对 (source_entity, target_entity)。例如：
   - 某单位与某制度之间的“制定 / 执行”关系；
   - 某岗位与某职责之间的“负责”关系；
   - 某问题与某法规条款之间的“违反 / 适用”关系；
   等等。
2. 对每一对存在关系的实体，至少需要提取以下信息：
   - source_entity：源实体名称，必须是前面实体列表中出现过的 entity_name。
   - target_entity：目标实体名称，同样必须来自实体列表。
   - relationship_description：用一句话解释这两个实体之间的关系是什么。
   - relationship_strength：一个数值，表示关系强度或置信度（例如 0~1 范围内的小数，或者 1~10 的整数）。
3. 每条关系必须使用如下 tuple 形式表示：
   ("relationship"%s<source_entity>%s<target_entity>%s<relationship_description>%s<relationship_strength>)

- 输出格式要求 -
1. 将所有实体和所有关系组成一个“单一列表”输出。
2. 使用 %s 作为各条记录之间的分隔符（record_delimiter）。
3. 每条记录内部使用 %s 作为字段分隔符（tuple_delimiter）。
4. 当所有输出完成之后，追加输出 %s 作为结束标记（completion_delimiter）。
5. 不得输出除上述列表以外的任何解释性文字、自然语言说明或 markdown 格式。

- 示例（仅结构示意，非真实答案） -
("entity"%s"城市规划法"%s"法律法规"%s"规范城市规划活动的上位法依据")%s
("entity"%s"土地使用"%s"主题"%s"与建设用地、耕地等用途划分相关")%s
("relationship"%s"城市规划法"%s"土地使用"%s"城市规划法对土地使用提出约束要求"%s"0.9")%s
%s

请严格按照以上格式输出。
""".formatted(
                safeTypes,
                tupleDelimiter, tupleDelimiter, tupleDelimiter,
                tupleDelimiter, tupleDelimiter, tupleDelimiter, tupleDelimiter,
                recordDelimiter,
                tupleDelimiter,
                completionDelimiter,
                tupleDelimiter, tupleDelimiter, tupleDelimiter, recordDelimiter,
                tupleDelimiter, tupleDelimiter, tupleDelimiter, recordDelimiter,
                tupleDelimiter, tupleDelimiter, tupleDelimiter, tupleDelimiter, recordDelimiter,
                completionDelimiter
        );
    }

    /**
     * Continue Prompt，对应 Python 的 CONTINUE_PROMPT。
     * 作为 user message 发送。
     */
    public static final String CONTINUE_PROMPT_ZH = """
上一轮抽取中仍有实体或关系被遗漏。
请在保持原有输出格式完全不变的前提下，继续从同一批文本中补充新的实体和关系。

要求：
1. 继续使用完全相同的 tuple 形式：
   - ("entity"<tuple_delimiter>...<tuple_delimiter>...<tuple_delimiter>...)
   - ("relationship"<tuple_delimiter>...<tuple_delimiter>...<tuple_delimiter>...<tuple_delimiter>...)
2. 继续使用完全相同的 record_delimiter 作为记录分隔符。
3. 只输出本轮新增的实体和关系记录，不要重复前面已经输出过的内容。
4. 不要输出任何解释性文字或说明，只输出记录。
""";

    /**
     * Loop Prompt，对应 Python 的 LOOP_PROMPT。
     * 作为 user message 发送，只允许返回 "Y" 或 "N"。
     */
    public static final String LOOP_PROMPT_ZH = """
请检查到目前为止你已经抽取出的实体和关系列表，
判断是否仍然存在需要继续补充的实体或关系。

如果你认为仍然需要继续抽取，请只输出一个大写字母 Y；
如果你认为已经没有需要补充的内容，请只输出一个大写字母 N。

不要输出除 Y 或 N 以外的任何内容，不要添加其他文字、标点或空格。
""";

    private GraphExtractionPromptsZh() {}
}
