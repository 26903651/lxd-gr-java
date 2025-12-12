package com.gdin.inspection.graphrag.v2.storage;

import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.Document;
import com.gdin.inspection.graphrag.v2.models.Embedding;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.util.List;

/**
 * 对齐 Python 端的 parquet 表：
 * entities / relationships / communities / community_reports /
 * text_units / documents / covariates / embeddings ...
 *
 * 这里只是抽象接口，具体用 Milvus 实现。
 */
public interface GraphRagIndexStorage {

    // ===== 实体 & 关系 =====

    void saveEntities(List<Entity> entities);

    List<Entity> loadEntities();

    void saveRelationships(List<Relationship> relationships);

    List<Relationship> loadRelationships();

    // ===== 文本单元 & 文档 =====

    void saveTextUnits(List<TextUnit> textUnits);

    List<TextUnit> loadTextUnits();

    void saveDocuments(List<Document> documents);

    List<Document> loadDocuments();

    // ===== 社区 & 报告 =====

    void saveCommunities(List<Community> communities);

    List<Community> loadCommunities();

    void saveCommunityReports(List<CommunityReport> reports);

    List<CommunityReport> loadCommunityReports();

    // ===== 共变量 / claim =====

    void saveCovariates(List<Covariate> covariates);

    List<Covariate> loadCovariates();

    // ===== 向量（可选，看你 Embedding 的用处）=====

    void saveEmbeddings(List<Embedding> embeddings);

    List<Embedding> loadEmbeddings();
}
