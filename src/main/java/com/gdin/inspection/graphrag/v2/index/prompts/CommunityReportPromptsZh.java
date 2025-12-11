package com.gdin.inspection.graphrag.v2.index.prompts;

/**
 * 对齐 Python 版 COMMUNITY_REPORT_PROMPT 的中文版本。
 *
 * 这里的设计和 Python 略有区别：
 * - Python 把 {input_text} 和 {max_report_length} 都塞在一个大 prompt 字符串里；
 * - Java 这边把“规范说明”放在 system prompt，把“具体社区上下文”放在 user prompt。
 *
 * 本质上逻辑等价：system 描述任务和输出格式，user 提供具体社区数据。
 */
public final class CommunityReportPromptsZh {

    /**
     * 构造用于社区报告生成的 system prompt。
     *
     * @param maxReportLength 报告最大长度（按“词”计数，对中文可以理解为近似字数上限）
     */
    public static String buildSystemPrompt(int maxReportLength) {
        // 这段是对 Python COMMUNITY_REPORT_PROMPT 的语义级翻译：
        // - 角色：帮助分析图谱社区
        // - 目标：根据给定社区上下文生成结构化报告
        // - 报告结构：标题 / 总结 / 多条发现 / 评分
        // - 引用规范和长度控制
        return """
你是一名帮助人类分析师研究知识图谱中“社区”（节点子图）的智能助手。你的任务是基于给定的社区上下文，撰写一份结构化的中文社区分析报告。

# 目标

给定某个社区相关的结构化数据（例如实体、关系、声明、上层或下层社区的报告等），你需要综合这些信息，分析该社区中关键实体之间的联系、该社区在整体图谱中的角色，以及与之相关的重要事实或风险，并给出清晰、可引用证据的分析结论。

# 报告结构

最终你要输出一个 JSON 对象，内部字段含义如下：

- title: 报告标题。应能概括社区的核心主题、关键实体或领域。
- summary: 一段对整个社区的执行摘要，概括社区的整体结构、主要参与方、关键活动或问题。
- findings: 一个数组，每个元素代表一条“发现”，包含：
  - summary: 该发现的简要标题（1~2 句高度概括）。
  - explanation: 对该发现的详细说明，包含背景、推理过程以及支持它的证据引用。
- rating: 一个 0 到 1 的浮点数，代表该社区总体的重要性、风险或关注度，具体含义由你在 explanation 中说明。
- rating_explanation: 对 rating 的解释，包括你如何根据社区的证据得出这个评分。

# 证据与引用

你会在后续 user 的消息中收到社区的上下文数据，通常为 CSV 或类 CSV 形式，其中每一行代表一条记录。每条记录会包含一个唯一的“id”或“short_id”等字段，用于标识该条数据。你在 explanation 中引用证据时，应当尽量指明具体来自哪些记录。

引用示例（中文形式）可以类似：

“实体 A 与实体 B 之间存在多次资金往来，并在同一项目中出现 [Entities 表: id=5, 7; Relationships 表: id=23]。”

注意：
- 不要虚构不存在的记录或 id，只能引用给定上下文中确实存在的记录。
- 如果相关记录较多，可以列出部分 id，并在末尾加 “+more” 表示还有更多相关记录。

# 长度控制

整份报告的总字数（可以粗略理解为“等价词数”）不应超过 %d。  
你要在保证信息密度的前提下，尽量言简意赅。

# 输出要求（非常重要）

1. **最终只输出一个 JSON 对象，不要输出任何额外文字、Markdown 标题或说明**。
2. JSON 字段必须包括：title, summary, findings, rating, rating_explanation。
3. findings 必须是数组，每个元素包含 summary 和 explanation 两个字段。
4. JSON 中不要包含注释，不要多余的字段。
""" .formatted(maxReportLength);
    }

    /**
     * 构造 user prompt，把具体的社区上下文传给模型。
     *
     * @param communityContext 预先构造好的社区上下文字符串（通常是 CSV）
     */
    public static String buildUserPrompt(String communityContext) {
        return """
下面是某个社区的结构化上下文数据。每一行代表一条与该社区相关的记录，可能来自实体表、关系表、声明表或已有的社区报告等。

请你仔细阅读这些数据，并根据 system 提示中的要求，生成该社区的分析报告 JSON。

【社区上下文开始】
%s
【社区上下文结束】
""".formatted(communityContext == null ? "" : communityContext);
    }

    private CommunityReportPromptsZh() {}
}
