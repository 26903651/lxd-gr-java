package com.gdin.inspection.graphrag.v2.index.prompts;

/**
 * 对齐 Python: graphrag/prompts/index/extract_claims.py
 */
public class ExtractClaimsPromptsZh {

    public static final String EXTRACT_CLAIMS_PROMPT = """
-目标任务-
你是一名智能助手，帮助人工分析员分析文本文件中针对特定实体提出的指控或主张。

-目标-
给定一份可能与本任务相关的文本文档、一个实体规格说明以及一个指控描述，抽取所有符合实体规格说明的实体，并抽取所有针对这些实体的指控或主张。

-步骤-
1. 抽取所有与预定义实体规格说明相匹配的命名实体。实体规格说明可以是实体名称列表，也可以是实体类型列表。
2. 对步骤 1 中识别出的每个实体，抽取所有与该实体相关的指控或主张。指控或主张必须符合指定的指控描述，并且该实体应当是该指控或主张的主体。
对每条指控或主张，抽取以下信息：
- Subject：指控或主张的主体实体名称。主体实体是实施了指控或主张中所描述行为的实体。Subject 必须是步骤 1 中识别出的命名实体之一。
- Object：指控或主张的客体实体名称。客体实体是报告/处理该行为的实体，或受到该行为影响的实体。如果客体实体未知，使用 **NONE**。
- Claim Type：指控或主张的总体类别。命名方式应当可以在多个文本输入之间复用，使相似的指控或主张具有相同的 Claim Type。
- Claim Status：**TRUE**、**FALSE** 或 **SUSPECTED**。TRUE 表示指控已被证实，FALSE 表示指控被证伪，SUSPECTED 表示指控尚未核实。
- Claim Description：对该指控或主张的详细描述，解释形成该指控或主张的理由，并包含所有相关证据与引用依据（仅基于文本，不要杜撰）。
- Claim Date：指控或主张发生的时间区间 (start_date, end_date)。start_date 与 end_date 均应为 ISO-8601 格式。如果指控发生在单一日期而不是日期区间，则 start_date 与 end_date 设为同一天。如果日期未知，返回 **NONE**。
- Claim Source Text：从原始文本中摘录的、与该指控或主张相关的**全部**引用原文（quotes）列表。

将每条指控或主张按如下格式输出：
(<subject_entity>{tuple_delimiter}<object_entity>{tuple_delimiter}<claim_type>{tuple_delimiter}<claim_status>{tuple_delimiter}<claim_start_date>{tuple_delimiter}<claim_end_date>{tuple_delimiter}<claim_description>{tuple_delimiter}<claim_source>)

3. 将步骤 1 与步骤 2 中识别出的所有指控或主张，以“单个列表”的形式用中文输出。列表项之间使用 **{record_delimiter}** 作为分隔符。

4. 完成后，输出 {completion_delimiter}

-示例-
示例 1：
Entity specification: 组织
Claim description: 与实体相关的风险信号
Text: 根据 2022/01/10 的一篇文章，公司 A 在参与政府机构 B 发布的多项公共招标时因串通投标被处罚。该公司由个人 C 持有，个人 C 在 2015 年被怀疑从事腐败活动。
Output:

(公司A{tuple_delimiter}政府机构B{tuple_delimiter}反竞争行为{tuple_delimiter}TRUE{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}根据 2022/01/10 的文章，公司A在参与政府机构B发布的多项公共招标过程中因串通投标被处罚，因此存在反竞争行为方面的风险信号{tuple_delimiter}根据 2022/01/10 的一篇文章，公司 A 在参与政府机构 B 发布的多项公共招标时因串通投标被处罚。)
{completion_delimiter}

示例 2：
Entity specification: 公司A,个人C
Claim description: 与实体相关的风险信号
Text: 根据 2022/01/10 的一篇文章，公司 A 在参与政府机构 B 发布的多项公共招标时因串通投标被处罚。该公司由个人 C 持有，个人 C 在 2015 年被怀疑从事腐败活动。
Output:

(公司A{tuple_delimiter}政府机构B{tuple_delimiter}反竞争行为{tuple_delimiter}TRUE{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}根据 2022/01/10 的文章，公司A在参与政府机构B发布的多项公共招标过程中因串通投标被处罚，因此存在反竞争行为方面的风险信号{tuple_delimiter}根据 2022/01/10 的一篇文章，公司 A 在参与政府机构 B 发布的多项公共招标时因串通投标被处罚。)
{record_delimiter}
(个人C{tuple_delimiter}NONE{tuple_delimiter}腐败{tuple_delimiter}SUSPECTED{tuple_delimiter}2015-01-01T00:00:00{tuple_delimiter}2015-12-30T00:00:00{tuple_delimiter}文本称个人C在 2015 年被怀疑从事腐败活动，该指控尚未核实，因此状态为 SUSPECTED{tuple_delimiter}该公司由个人 C 持有，个人 C 在 2015 年被怀疑从事腐败活动。)
{completion_delimiter}

-真实数据-
请使用以下输入生成你的回答。
Entity specification: {entity_specs}
Claim description: {claim_description}
Text: {input_text}
Output:""";

    public static final String CONTINUE_PROMPT = "上一次抽取遗漏了很多实体。请使用相同格式在下方补充：\n";

    public static final String LOOP_PROMPT = "看起来仍可能遗漏了一些实体。如果仍有需要补充的实体，请回答 Y；如果没有，请回答 N。请只用单个字母 Y 或 N 作答。\n";
}
