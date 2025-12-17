package com.gdin.inspection.graphrag.v2;

import cn.hutool.core.io.FileUtil;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.CreateCommunitiesOperation;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.GraphExtractor;
import com.gdin.inspection.graphrag.v2.index.opertation.SummarizeDescriptionsOperation;
import com.gdin.inspection.graphrag.v2.index.opertation.SummarizeCommunitiesOperation;
import com.gdin.inspection.graphrag.v2.index.cluster.GraphClusterClient;
import com.gdin.inspection.graphrag.v2.index.cluster.LeidenCluster;
import com.gdin.inspection.graphrag.v2.index.workflows.ExtractGraphWorkflow;
import com.gdin.inspection.graphrag.v2.index.workflows.LoadInputDocumentsWorkflow;
import com.gdin.inspection.graphrag.v2.models.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class ExtractionTest {
    @Resource
    private LoadInputDocumentsWorkflow loadInputDocumentsWorkflow;

    @Resource
    private GraphExtractor graphExtractor;

    @Resource
    private SummarizeDescriptionsOperation summarizeDescriptionsOperation;

    @Resource
    private GraphClusterClient graphClusterClient;

    @Resource
    private ExtractGraphWorkflow extractGraphWorkflow;

    @Resource
    private CreateCommunitiesOperation createCommunitiesOperation;

    @Resource
    private SummarizeCommunitiesOperation summarizeCommunitiesOperation;

    @Test
    void testExtraction() throws Exception {
        String entityTypes = "组织, 人物, 地点, 事件, 时间";
        List<TextUnit> textUnits = loadInputDocumentsWorkflow.run(List.of("9c3c1d6b-fd37-42b9-8b33-132da0beb6cd"));
        GraphExtractor.ExtractionResult extractionResult = graphExtractor.extract(textUnits, entityTypes);
        FileUtil.writeString(IOUtil.jsonSerializeWithNoType(extractionResult), "D:\\test\\extractionResult.json", StandardCharsets.UTF_8);
    }

    @Test
    void testSummarize() throws Exception {
        byte[] bytes = FileUtil.readBytes("D:\\test\\extractionResult.json");
        GraphExtractor.ExtractionResult extractionResult = IOUtil.jsonDeserializeWithNoType(new String(bytes, StandardCharsets.UTF_8), GraphExtractor.ExtractionResult.class);
        List<EntityDescriptionSummary> entityDescriptionSummaries = summarizeDescriptionsOperation.summarizeEntities(extractionResult.getEntities(), 150);
        log.info("entityDescriptionSummaries: {}", entityDescriptionSummaries);
        List<RelationshipDescriptionSummary> relationshipDescriptionSummaries = summarizeDescriptionsOperation.summarizeRelationships(extractionResult.getRelationships(), 150);
        log.info("relationshipDescriptionSummaries: {}", relationshipDescriptionSummaries);
    }

    @Test
    void testLeiden(){
        Relationship relationship = Relationship.builder().source("A").target("B").weight(1d).build();
        List<LeidenCluster> leidenClusters = graphClusterClient.clusterGraph(List.of(relationship), 50, true, 42);
        log.info("leidenClusters: {}", leidenClusters);
    }

    @Test
    void testIndex1() throws Exception {
        String entityTypes = "组织, 人物, 地点, 事件, 时间";
        List<TextUnit> textUnits = loadInputDocumentsWorkflow.run(List.of("9c3c1d6b-fd37-42b9-8b33-132da0beb6cd"));
        FileUtil.writeString(IOUtil.jsonSerialize(textUnits), "D:\\test\\textUnits.json", StandardCharsets.UTF_8);
        ExtractGraphWorkflow.Result result = extractGraphWorkflow.run(textUnits, entityTypes, 150, 150);
        FileUtil.writeString(IOUtil.jsonSerialize(result.getEntities()), "D:\\test\\entities.json", StandardCharsets.UTF_8);
        FileUtil.writeString(IOUtil.jsonSerialize(result.getRelationships()), "D:\\test\\relationShips.json", StandardCharsets.UTF_8);
    }

    @Test
    void testIndex2() throws Exception {
        byte[] bytes = FileUtil.readBytes("D:\\test\\entities.json");
        List<Entity> entities = (List<Entity>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        bytes = FileUtil.readBytes("D:\\test\\relationShips.json");
        List<Relationship> relationships = (List<Relationship>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        List<Community> communities = createCommunitiesOperation.createCommunities(entities, relationships, 50, true, 42);
        FileUtil.writeString(IOUtil.jsonSerialize(communities), "D:\\test\\communities.json", StandardCharsets.UTF_8);
    }

    @Test
    void testIndex3() throws Exception {
        byte[] bytes = FileUtil.readBytes("D:\\test\\textUnits.json");
        List<TextUnit> textUnits = (List<TextUnit>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        bytes = FileUtil.readBytes("D:\\test\\entities.json");
        List<Entity> entities = (List<Entity>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        bytes = FileUtil.readBytes("D:\\test\\relationShips.json");
        List<Relationship> relationships = (List<Relationship>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        bytes = FileUtil.readBytes("D:\\test\\communities.json");
        List<Community> communities = (List<Community>) IOUtil.jsonDeserialize(new String(bytes, StandardCharsets.UTF_8));
        List<CommunityReport> communityReports =
                summarizeCommunitiesOperation.summarizeCommunities(
                        communities,
                        entities,
                        relationships,
                        textUnits,
                        1500 // maxReportLength，可以和 Python 配置保持一致
                );
        FileUtil.writeString(IOUtil.jsonSerialize(communityReports), "D:\\test\\communityReports.json", StandardCharsets.UTF_8);
    }
}
