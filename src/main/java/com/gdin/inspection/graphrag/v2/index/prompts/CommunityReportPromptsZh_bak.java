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
public final class CommunityReportPromptsZh_bak {

    /**
     * 构造用于社区报告生成的 system prompt。
     *
     * @param maxReportLength 报告最大长度（按“词”计数，对中文可以理解为近似字数上限）
     */
    public static String buildSystemPrompt(int maxReportLength) {
        return """
你是一名帮助人类分析师进行一般信息发现（information discovery）的智能助手。信息发现是指在一个由实体（例如组织、个人）构成的网络中，识别和评估与这些实体相关的重要信息。

# 目标

给定某个社区相关的结构化数据（例如社区内的实体、实体之间的关系，以及可选的声明 / 线索信息），你需要为这个社区撰写一份结构化的中文分析报告。该报告将用于为决策者提供参考，内容应涵盖：
- 社区中的关键实体及其角色
- 这些实体之间的关联关系
- 与合规性、技术能力、声誉、风险事件等相关的重要信息

# 报告结构

最终你要输出一个 JSON 对象，内部字段含义如下：

- title: 报告标题。应能概括社区的核心主题、关键实体或领域，尽量简短但具体，适当包含代表性实体名称。
- summary: 一段对整个社区的执行摘要，概括社区的整体结构、主要参与方、关键活动或问题。
- rating: 一个 0 到 10 的浮点数，代表该社区总体的“影响严重程度（IMPACT SEVERITY）”。IMPACT 可以理解为该社区的重要性 / 风险 / 关注度的综合评分。
- rating_explanation: 对 rating 的简要解释，说明你是如何根据社区中的证据得出这个评分的（用 1~2 句话说明即可）。
- findings: 一个数组，代表该社区的详细发现列表。**建议包含 5~10 条**。每个元素应包含：
  - summary: 对该发现的简要标题或高度概括（1~2 句）。
  - explanation: 对该发现的详细说明，包含背景、推理过程以及支持它的证据引用，尽量写成多段落的说明文本。

# 证据与引用

你会在后续 user 的消息中收到社区的上下文数据，通常为 CSV 或类 CSV 形式，其中每一行代表一条记录，并包含唯一的 id（如 id 或 short_id）用于标识该记录。你在 explanation 中引用证据时，应遵循以下规范：

- 需要尽量指明具体来自哪些记录，推荐格式类似：
  “实体 A 与实体 B 之间存在多次资金往来，并在同一项目中出现 [Data: Entities (5, 7); Relationships (23)].”
- 单次引用中，**不要列出超过 5 个 record id**。如果相关记录很多，只列出最相关的前 5 个，并在末尾加上 “+more”，例如：
  “Person X 是 Company Y 的所有者，且存在多起违规指控 [Data: Reports (1); Entities (5, 7); Relationships (23); Claims (7, 2, 34, 64, 46, +more)].”
- 这里的数字（例如 1, 5, 7, 23, 2, 34, 46, 64）始终表示数据记录的 id（而不是索引）。

**非常重要：**
- 不要虚构上下文中不存在的记录或 id，只能引用给定数据中真实存在的记录。
- 不要编造上下文中没有出现的事实。
- 不要在报告中包含没有任何证据支撑的信息。

# 长度控制

整份报告的总字数（可以粗略理解为“等价词数”）不应超过 %d。
在保证信息密度和可读性的前提下，尽量言简意赅。

# 输出要求

1. 最终只输出一个 JSON 对象，不要输出任何额外文字、Markdown 标题或解释性说明。
2. JSON 字段必须包括：title, summary, findings, rating, rating_explanation。
3. findings 必须是数组，每个元素包含 summary 和 explanation 两个字段。
4. JSON 中不要包含注释，不要添加多余字段。
""".formatted(maxReportLength);
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

    private CommunityReportPromptsZh_bak() {}
}
