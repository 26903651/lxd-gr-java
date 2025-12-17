package com.gdin.inspection.graphrag.v2.index.prompts;

/**
 * 对齐 Python: graphrag/prompts/index/extract_claims.py
 *
 * 注意：
 * - 仅将英文 prompt 翻译为中文（你要求中文环境）
 * - 保留占位符与 delimiter 变量名：{tuple_delimiter} / {record_delimiter} / {completion_delimiter}
 * - 保留关键输出 token：TRUE/FALSE/SUSPECTED、NONE、ISO-8601
 */
public class ExtractClaimsPromptsZh {

    private ExtractClaimsPromptsZh() {}

    public static final String EXTRACT_CLAIMS_PROMPT = """
            -目标活动-
            你是一名智能助手，帮助人类分析员对文本中涉及特定实体的“主张/声明(Claim)”进行分析抽取。
            
            -目标-
            给定一份可能与该活动相关的文本、实体规格说明(entity specification)以及主张描述(claim description)，
            抽取所有符合实体规格说明的实体，并抽取针对这些实体的所有主张。
            
            -步骤-
            1. 抽取所有符合预定义实体规格说明的命名实体。实体规格说明可以是实体名称列表，也可以是实体类型列表。
            2. 对于步骤1中识别出的每个实体，抽取所有与该实体相关的主张。主张必须符合指定的主张描述，并且该实体应作为主张的“主体(subject)”。
               对每条主张，抽取以下信息：
               - Subject：主张主体实体名称（大写）。主体实体是执行该主张所述行为的实体。Subject 必须是步骤1识别出的命名实体之一。
               - Object：主张客体实体名称（大写）。客体实体是报告/处理该行为或受该行为影响的实体。如果客体实体未知，使用 **NONE**。
               - Claim Type：主张类型（大写）。用可复用的方式命名，使得跨多段文本的相似主张能共享同一种类型。
               - Claim Status：**TRUE**、**FALSE** 或 **SUSPECTED**。TRUE 表示主张已被证实，FALSE 表示主张被证伪，SUSPECTED 表示主张未被验证。
               - Claim Description：对主张进行详细描述，说明主张成立的原因、所有相关证据与引用。
               - Claim Date：主张发生的时间区间(start_date, end_date)。start_date 和 end_date 都必须是 ISO-8601 格式。
                 如果是单一日期，则 start_date 与 end_date 填同一日期。如果日期未知，返回 **NONE**。
               - Claim Source Text：与该主张相关的**所有**原文引述列表。
            
               将每条主张按如下格式输出：
               (<subject_entity>{tuple_delimiter}<object_entity>{tuple_delimiter}<claim_type>{tuple_delimiter}<claim_status>{tuple_delimiter}<claim_start_date>{tuple_delimiter}<claim_end_date>{tuple_delimiter}<claim_description>{tuple_delimiter}<claim_source>)
            
            3. 以单个列表返回上述所有主张。使用 **{record_delimiter}** 作为列表分隔符。
            
            4. 当完成后，输出 {completion_delimiter}
            
            -示例-
            示例 1：
            Entity specification: organization
            Claim description: red flags associated with an entity
            Text: According to an article on 2022/01/10, Company A was fined for bid rigging while participating in multiple public tenders published by Government Agency B. The company is owned by Person C who was suspected of engaging in corruption activities in 2015.
            Output:
            
            (COMPANY A{tuple_delimiter}GOVERNMENT AGENCY B{tuple_delimiter}ANTI-COMPETITIVE PRACTICES{tuple_delimiter}TRUE{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}Company A was found to engage in anti-competitive practices because it was fined for bid rigging in multiple public tenders published by Government Agency B according to an article published on 2022/01/10{tuple_delimiter}According to an article published on 2022/01/10, Company A was fined for bid rigging while participating in multiple public tenders published by Government Agency B.)
            {completion_delimiter}
            
            示例 2：
            Entity specification: Company A, Person C
            Claim description: red flags associated with an entity
            Text: According to an article on 2022/01/10, Company A was fined for bid rigging while participating in multiple public tenders published by Government Agency B. The company is owned by Person C who was suspected of engaging in corruption activities in 2015.
            Output:
            
            (COMPANY A{tuple_delimiter}GOVERNMENT AGENCY B{tuple_delimiter}ANTI-COMPETITIVE PRACTICES{tuple_delimiter}TRUE{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}2022-01-10T00:00:00{tuple_delimiter}Company A was found to engage in anti-competitive practices because it was fined for bid rigging in multiple public tenders published by Government Agency B according to an article published on 2022/01/10{tuple_delimiter}According to an article published on 2022/01/10, Company A was fined for bid rigging while participating in multiple public tenders published by Government Agency B.)
            {record_delimiter}
            (PERSON C{tuple_delimiter}NONE{tuple_delimiter}CORRUPTION{tuple_delimiter}SUSPECTED{tuple_delimiter}2015-01-01T00:00:00{tuple_delimiter}2015-12-30T00:00:00{tuple_delimiter}Person C was suspected of engaging in corruption activities in 2015{tuple_delimiter}The company is owned by Person C who was suspected of engaging in corruption activities in 2015)
            {completion_delimiter}
            
            -真实数据-
            使用以下输入生成你的答案。
            Entity specification: {entity_specs}
            Claim description: {claim_description}
            Text: {input_text}
            Output:""";

    public static final String CONTINUE_PROMPT =
            "上一次抽取遗漏了很多实体。请按相同格式在下面补充遗漏的实体与主张：\n";

    public static final String LOOP_PROMPT =
            "看起来可能仍有一些实体被遗漏。如果仍需要补充遗漏实体请回答 Y，如果没有请回答 N。请只输出单个字母 Y 或 N。\n";
}
