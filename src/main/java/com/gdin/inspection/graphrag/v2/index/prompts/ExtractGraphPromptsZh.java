package com.gdin.inspection.graphrag.v2.index.prompts;

public class ExtractGraphPromptsZh {

    public static final String GRAPH_EXTRACTION_PROMPT = """
-目标-
给定一份可能与本任务相关的文本文档，以及一个实体类型列表，从文本中识别出所有属于这些类型的实体，并识别这些实体之间的所有关系。

-步骤-
1. 识别所有实体。对每个被识别出的实体，抽取以下信息：
- entity_name：实体名称，优先使用原文中的标准称呼；如存在同义/别名，请选择最常用或最正式的那个作为名称
- entity_type：以下类型之一：[{entity_types}]
- entity_description：用中文给出对该实体属性、角色、行为与活动的全面描述（仅基于文本，不要杜撰）
将每个实体按如下格式输出：
("entity"{tuple_delimiter}<entity_name>{tuple_delimiter}<entity_type>{tuple_delimiter}<entity_description>)

2. 基于步骤 1 中识别出的实体，找出所有彼此之间“明确相关”的实体对 (source_entity, target_entity)。
对每一对相关实体，抽取以下信息：
- source_entity：源实体名称，必须与步骤 1 中识别的 entity_name 完全一致
- target_entity：目标实体名称，必须与步骤 1 中识别的 entity_name 完全一致
- relationship_description：用中文说明你为何认为源实体与目标实体彼此相关（依据必须来自文本，不要猜测）
- relationship_strength：一个数值分数，用于表示源实体与目标实体之间关系强度（数值越大表示越强）
将每条关系按如下格式输出：
("relationship"{tuple_delimiter}<source_entity>{tuple_delimiter}<target_entity>{tuple_delimiter}<relationship_description>{tuple_delimiter}<relationship_strength>)

3. 将步骤 1 和步骤 2 中识别出的所有实体与关系，以“单个列表”的形式用中文输出。列表项之间使用 **{record_delimiter}** 作为分隔符。

4. 完成后，输出 {completion_delimiter}

######################
-示例-
######################
示例 1：
Entity_types: 组织,人物
Text:
Verdantis 的中央机构计划在周一和周四召开会议，该机构计划在周四下午 1:30（PDT）发布最新的政策决定，随后举行新闻发布会，中央机构主席 Martin Smith 将回答提问。投资者预计市场策略委员会将把基准利率维持在 3.5%–3.75% 区间不变。
######################
Output:
("entity"{tuple_delimiter}中央机构{tuple_delimiter}组织{tuple_delimiter}中央机构是 Verdantis 的中央金融决策机构，计划在周一与周四召开会议，并将在周四发布最新政策决定并举行新闻发布会)
{record_delimiter}
("entity"{tuple_delimiter}Martin Smith{tuple_delimiter}人物{tuple_delimiter}Martin Smith 是中央机构主席，将在新闻发布会上回答提问)
{record_delimiter}
("entity"{tuple_delimiter}市场策略委员会{tuple_delimiter}组织{tuple_delimiter}市场策略委员会是中央机构相关的决策机构，投资者预计其将把基准利率维持在 3.5%–3.75% 区间)
{record_delimiter}
("relationship"{tuple_delimiter}Martin Smith{tuple_delimiter}中央机构{tuple_delimiter}文本明确说明 Martin Smith 担任中央机构主席，并代表该机构在新闻发布会上回答提问{tuple_delimiter}9)
{completion_delimiter}

######################
示例 2：
Entity_types: 组织
Text:
TechGlobal（TG）的股价在周四于全球交易所上市首日暴涨。但 IPO 专家警告称，这家半导体公司的公开市场首秀并不能代表其他新上市公司的表现。

TechGlobal 曾是一家上市公司，2014 年被 Vision Holdings 私有化收购。这家成熟的芯片设计公司称其为 85% 的高端智能手机提供动力。
######################
Output:
("entity"{tuple_delimiter}TechGlobal{tuple_delimiter}组织{tuple_delimiter}TechGlobal 是一家半导体芯片设计公司，周四在全球交易所上市首日股价大涨，并声称其产品为 85% 的高端智能手机提供动力)
{record_delimiter}
("entity"{tuple_delimiter}Vision Holdings{tuple_delimiter}组织{tuple_delimiter}Vision Holdings 是一家在 2014 年将 TechGlobal 私有化收购的机构)
{record_delimiter}
("relationship"{tuple_delimiter}TechGlobal{tuple_delimiter}Vision Holdings{tuple_delimiter}文本明确说明 Vision Holdings 在 2014 年对 TechGlobal 实施私有化收购，二者存在明确的股权收购关系{tuple_delimiter}5)
{completion_delimiter}

######################
示例 3：
Entity_types: 组织,地点,人物
Text:
五名 Aurelia 人在 Firuzabad 被关押 8 年并被广泛视为人质，目前正在返回 Aurelia。

由 Quintara 促成的交换在 80 亿美元的 Firuzi 资金被转入 Quintara 首都 Krohaara 的金融机构后最终完成。

交换在 Firuzabad 首都 Tiruzia 启动，四名男子和一名女子（同时也是 Firuzi 国民）登上一架包机前往 Krohaara。

他们受到 Aurelia 高级官员的欢迎，现正前往 Aurelia 首都 Cashion。

这些 Aurelia 人包括 39 岁商人 Samuel Namara，他曾被关押在 Tiruzia 的 Alhamia Prison；以及 59 岁记者 Durke Bataglani 与 53 岁环保人士 Meggie Tazbah，后者也拥有 Bratinas 国籍。
######################
Output:
("entity"{tuple_delimiter}Firuzabad{tuple_delimiter}地点{tuple_delimiter}Firuzabad 是关押 Aurelia 人并被视为人质事件发生的地点，其首都为 Tiruzia)
{record_delimiter}
("entity"{tuple_delimiter}Aurelia{tuple_delimiter}地点{tuple_delimiter}Aurelia 是相关人员返回的国家，其高级官员迎接获释人员并安排其前往首都 Cashion)
{record_delimiter}
("entity"{tuple_delimiter}Quintara{tuple_delimiter}地点{tuple_delimiter}Quintara 是促成交换的国家，其首都 Krohaara 的金融机构接收了相关资金转入)
{record_delimiter}
("entity"{tuple_delimiter}Tiruzia{tuple_delimiter}地点{tuple_delimiter}Tiruzia 是 Firuzabad 的首都，交换在此启动，相关人员曾在此地被关押)
{record_delimiter}
("entity"{tuple_delimiter}Krohaara{tuple_delimiter}地点{tuple_delimiter}Krohaara 是 Quintara 的首都，相关资金转入该地金融机构，获释人员也乘包机前往该地)
{record_delimiter}
("entity"{tuple_delimiter}Cashion{tuple_delimiter}地点{tuple_delimiter}Cashion 是 Aurelia 的首都，获释人员将前往该地)
{record_delimiter}
("entity"{tuple_delimiter}Samuel Namara{tuple_delimiter}人物{tuple_delimiter}Samuel Namara 是 39 岁商人，Aurelia 人之一，曾被关押在 Tiruzia 的 Alhamia Prison)
{record_delimiter}
("entity"{tuple_delimiter}Alhamia Prison{tuple_delimiter}地点{tuple_delimiter}Alhamia Prison 是位于 Tiruzia 的监狱，Samuel Namara 曾在此被关押)
{record_delimiter}
("entity"{tuple_delimiter}Durke Bataglani{tuple_delimiter}人物{tuple_delimiter}Durke Bataglani 是 59 岁记者，Aurelia 人之一，被描述为被关押并获释的人员)
{record_delimiter}
("entity"{tuple_delimiter}Meggie Tazbah{tuple_delimiter}人物{tuple_delimiter}Meggie Tazbah 是 53 岁环保人士，Aurelia 人之一，也拥有 Bratinas 国籍，被描述为被关押并获释的人员)
{record_delimiter}
("relationship"{tuple_delimiter}Firuzabad{tuple_delimiter}Aurelia{tuple_delimiter}文本描述 Aurelia 人在 Firuzabad 被关押并作为人质获释返国，二者在“关押与人质事件”上存在明确关联{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Quintara{tuple_delimiter}Aurelia{tuple_delimiter}文本明确说明 Quintara 促成交换并使 Aurelia 人获释返国，二者在“促成交换/人质释放”上存在明确关联{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Quintara{tuple_delimiter}Firuzabad{tuple_delimiter}文本表明交换在 Firuzabad 相关背景下进行且由 Quintara 促成，二者在“交换安排”上存在明确关联{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Samuel Namara{tuple_delimiter}Alhamia Prison{tuple_delimiter}文本明确说明 Samuel Namara 曾被关押在 Tiruzia 的 Alhamia Prison{tuple_delimiter}8)
{record_delimiter}
("relationship"{tuple_delimiter}Samuel Namara{tuple_delimiter}Meggie Tazbah{tuple_delimiter}两人同属本次获释并返回 Aurelia 的人员群体，文本指向其在同一人质释放事件中被交换释放{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Samuel Namara{tuple_delimiter}Durke Bataglani{tuple_delimiter}两人同属本次获释并返回 Aurelia 的人员群体，文本指向其在同一人质释放事件中被交换释放{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Meggie Tazbah{tuple_delimiter}Durke Bataglani{tuple_delimiter}两人同属本次获释并返回 Aurelia 的人员群体，文本指向其在同一人质释放事件中被交换释放{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Samuel Namara{tuple_delimiter}Firuzabad{tuple_delimiter}文本描述 Samuel Namara 在 Firuzabad 相关事件中被关押并被视为人质，因此二者相关{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Meggie Tazbah{tuple_delimiter}Firuzabad{tuple_delimiter}文本描述 Meggie Tazbah 在 Firuzabad 相关事件中被关押并被视为人质，因此二者相关{tuple_delimiter}2)
{record_delimiter}
("relationship"{tuple_delimiter}Durke Bataglani{tuple_delimiter}Firuzabad{tuple_delimiter}文本描述 Durke Bataglani 在 Firuzabad 相关事件中被关押并被视为人质，因此二者相关{tuple_delimiter}2)
{completion_delimiter}

######################
-真实数据-
######################
Entity_types: {entity_types}
Text: {input_text}
######################
Output:""";

    public static final String CONTINUE_PROMPT =
            "上一轮抽取遗漏了很多实体和关系。请继续补充遗漏项。注意：只输出与之前相同的实体类型，且必须使用完全相同的格式：\n";

    // 关键：仍要求单字符 Y/N，保证逻辑对齐 Python
    public static final String LOOP_PROMPT =
            "看起来仍可能有遗漏。若还有需要补充的实体或关系，请回答 Y；若没有，请回答 N。只回答单个字母 Y 或 N。\n";
}

