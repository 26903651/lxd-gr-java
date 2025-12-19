package com.gdin.inspection.graphrag.v2.index.prompts;

/**
 * 对齐 Python 版 COMMUNITY_REPORT_PROMPT 的中文版本，并增加“JSON 字符串内容禁用英文双引号”的统一规则。
 */
public final class CommunityReportPromptsZh {
    public static final String COMMUNITY_REPORT_PROMPT = """
你是一名 AI 助手，帮助人工分析员进行通用的信息发现工作。信息发现是指在一个网络中，识别并评估与某些实体（例如组织与个人）相关的有用信息的过程。

# 目标
在给定一个社区中包含的实体列表、这些实体之间的关系，以及可选的相关指控或主张的情况下，撰写一份关于该社区的综合报告。该报告将用于向决策者提供与该社区相关的信息及其潜在影响。报告内容包括社区关键实体概览、其合规情况、技术能力、声誉，以及值得关注的指控或主张。

# 输出引号规则
你必须严格遵守以下规则以确保输出可被 JSON 解析器解析：
1. 你的最终输出必须是一个标准 JSON 对象字符串，可被 JSON 解析器直接解析。
2. 任何 JSON 字符串“内容内部”严禁出现英文半角双引号 "。凡是需要引用或强调的内容，一律使用中文双引号 “ ”。
3. 如果输入文本、证据引用或摘要中包含英文半角双引号 "，你必须在输出时将其替换为中文双引号 “ ”，以避免破坏 JSON 结构。

# 报告结构

报告应包含以下部分：

- TITLE：能够代表该社区关键实体的社区名称。标题应简短但具体。在可能的情况下，在标题中包含具有代表性的命名实体。
- SUMMARY：社区整体结构的高层概述，说明社区内实体之间如何相互关联，以及与这些实体相关的重要信息。
- IMPACT SEVERITY RATING：0-10 之间的浮点评分，用于表示该社区内实体造成的影响严重程度。IMPACT 表示对一个社区重要性的评分。
- RATING EXPLANATION：用一句话解释该 IMPACT 严重程度评分的原因。
- DETAILED FINDINGS：列出 5-10 条关于该社区的关键洞察。每条洞察应包含一个简短摘要，并给出多段详细解释文字；解释必须按下方“证据对齐规则”进行引用支撑，且内容要全面。

请将输出以一个格式正确的 JSON 字符串返回，格式如下：
    {{
        "title": <report_title>,
        "summary": <executive_summary>,
        "rating": <impact_severity_rating>,
        "rating_explanation": <rating_explanation>,
        "findings": [
            {{
                "summary": <insight_1_summary>,
                "explanation": <insight_1_explanation>
            }},
            {{
                "summary": <insight_2_summary>,
                "explanation": <insight_2_explanation>
            }}
        ]
    }}

# 证据对齐规则

由数据支撑的观点需要按如下格式列出其数据引用：

“这是一句由多条数据引用支撑的示例句子 [Data: <数据集名称> (记录 id 列表); <数据集名称> (记录 id 列表)].”

单个引用中不要列出超过 5 个记录 id。应当列出最相关的前 5 个记录 id，并追加 “+more” 表示还有更多。

例如：
“人物 X 是公司 Y 的所有者，并受到多项不当行为指控 [Data: Reports (1), Entities (5, 7); Relationships (23); Claims (7, 2, 34, 64, 46, +more)].”

其中 1、5、7、23、2、34、46 与 64 表示相关数据记录的 id（不是索引位置）。

不要包含任何缺少证据支撑的信息。

将整份报告的总长度限制为 {max_report_length} 个词。

# 输入示例
-----------
Text:

实体（Entities）

id,entity,description
5,VERDANT OASIS PLAZA,Verdant Oasis Plaza 是 Unity March 的举办地点
6,HARMONY ASSEMBLY,Harmony Assembly 是一个组织，正在 Verdant Oasis Plaza 举办一次游行活动

关系（Relationships）

id,source,target,description
37,VERDANT OASIS PLAZA,UNITY MARCH,Verdant Oasis Plaza 是 Unity March 的举办地点
38,VERDANT OASIS PLAZA,HARMONY ASSEMBLY,Harmony Assembly 在 Verdant Oasis Plaza 举办游行
39,VERDANT OASIS PLAZA,UNITY MARCH,Unity March 正在 Verdant Oasis Plaza 举行
40,VERDANT OASIS PLAZA,TRIBUNE SPOTLIGHT,Tribune Spotlight 正在报道发生在 Verdant Oasis Plaza 的 Unity March
41,VERDANT OASIS PLAZA,BAILEY ASADI,Bailey Asadi 在 Verdant Oasis Plaza 就游行活动发表讲话
43,HARMONY ASSEMBLY,UNITY MARCH,Harmony Assembly 正在组织 Unity March

Output:
{{
    "title": "Verdant Oasis Plaza 与 Unity March",
    "summary": "该社区围绕 Verdant Oasis Plaza 展开，该地点是 Unity March 的举办地。广场与 Harmony Assembly、Unity March、Tribune Spotlight 以及 Bailey Asadi 等实体存在直接关联，这些关联共同指向同一场游行事件及其组织与传播链路。",
    "rating": 5.0,
    "rating_explanation": "该社区的影响严重程度为中等，因为与游行活动相关的聚集与传播可能带来一定的不确定性与风险外溢。",
    "findings": [
        {{
            "summary": "Verdant Oasis Plaza 是社区的核心枢纽",
            "explanation": "Verdant Oasis Plaza 是该社区的中心实体，作为 Unity March 的举办地点，它与多条关系记录相连，构成了社区网络的主要汇聚点。该地点作为事件载体，使组织方、事件本身、媒体与发言人都围绕其形成关联，因此它对理解社区结构与潜在影响具有关键意义。 [Data: Entities (5), Relationships (37, 38, 39, 40, 41, +more)]"
        }},
        {{
            "summary": "Harmony Assembly 是事件的组织核心之一",
            "explanation": "Harmony Assembly 在数据中被明确描述为在 Verdant Oasis Plaza 举办游行并组织 Unity March 的组织实体。其与地点及事件之间的关系，为识别该社区的组织结构提供了主线，也有助于评估事件的动员与组织能力。 [Data: Entities (6), Relationships (38, 43)]"
        }},
        {{
            "summary": "Unity March 是社区关系聚焦的关键事件",
            "explanation": "Unity March 在多条关系中与 Verdant Oasis Plaza 直接绑定，显示该事件是社区网络中的关键“共同指向点”。事件的存在把地点、组织与报道方串联起来，是理解社区动态与影响路径的重要切入点。 [Data: Relationships (37, 39, +more)]"
        }},
        {{
            "summary": "媒体与发言人节点提示了传播与舆论维度",
            "explanation": "Tribune Spotlight 的报道关系表明该事件获得媒体关注，可能扩大其影响范围；Bailey Asadi 在现场发言则提示存在“人物-地点-事件”的表达或动员链路。这些节点往往会影响外部认知与事件外溢效应的大小。 [Data: Relationships (40, 41)]"
        }}
    ]
}}

# 真实数据

请使用以下文本生成你的回答。不要在回答中编造任何信息。

Text:
{input_text}

报告应包含以下部分：

- TITLE：能够代表该社区关键实体的社区名称。标题应简短但具体。在可能的情况下，在标题中包含具有代表性的命名实体。
- SUMMARY：社区整体结构的高层概述，说明社区内实体之间如何相互关联，以及与这些实体相关的重要信息。
- IMPACT SEVERITY RATING：0-10 之间的浮点评分，用于表示该社区内实体造成的影响严重程度。IMPACT 表示对一个社区重要性的评分。
- RATING EXPLANATION：用一句话解释该 IMPACT 严重程度评分的原因。
- DETAILED FINDINGS：列出 5-10 条关于该社区的关键洞察。每条洞察应包含一个简短摘要，并给出多段详细解释文字；解释必须按下方“证据对齐规则”进行引用支撑，且内容要全面。

请将输出以一个格式正确的 JSON 字符串返回，格式如下：
    {{
        "title": <report_title>,
        "summary": <executive_summary>,
        "rating": <impact_severity_rating>,
        "rating_explanation": <rating_explanation>,
        "findings": [
            {{
                "summary": <insight_1_summary>,
                "explanation": <insight_1_explanation>
            }},
            {{
                "summary": <insight_2_summary>,
                "explanation": <insight_2_explanation>
            }}
        ]
    }}

# 证据对齐规则

由数据支撑的观点需要按如下格式列出其数据引用：

“这是一句由多条数据引用支撑的示例句子 [Data: <数据集名称> (记录 id 列表); <数据集名称> (记录 id 列表)].”

单个引用中不要列出超过 5 个记录 id。应当列出最相关的前 5 个记录 id，并追加 “+more” 表示还有更多。

不要包含任何缺少证据支撑的信息。

将整份报告的总长度限制为 {max_report_length} 个词。

Output:""";
}
