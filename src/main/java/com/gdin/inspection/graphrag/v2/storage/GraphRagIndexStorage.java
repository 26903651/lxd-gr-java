package com.gdin.inspection.graphrag.v2.storage;

import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.Document;
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
    public final int SCOPE_MAIN = 1;
    public final int SCOPE_DELTA = 2;

    // ===== 实体 & 关系 =====

    void saveEntities(int scope, List<Entity> entities);

    List<Entity> loadEntities(int scope);

    void saveRelationships(int scope, List<Relationship> relationships);

    List<Relationship> loadRelationships(int scope);

    // ===== 文本单元 & 文档 =====

    List<TextUnit> loadTextUnits(int scope);

    List<Document> loadDocuments(int scope);

    // ===== 社区 & 报告 =====

    void saveCommunities(int scope, List<Community> communities);

    List<Community> loadCommunities(int scope);

    void saveCommunityReports(int scope, List<CommunityReport> reports);

    List<CommunityReport> loadCommunityReports(int scope);

    // ===== 共变量 / claim =====

    void saveCovariates(int scope, List<Covariate> covariates);

    List<Covariate> loadCovariates(int scope);
}
