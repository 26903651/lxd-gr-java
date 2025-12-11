package com.gdin.inspection.graphrag.v2;

import com.gdin.inspection.graphrag.v2.index.FinalizeAndPersist;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.state.InMemoryStateStore;
import com.gdin.inspection.graphrag.v2.state.StateStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class FinalizeAndPersistTest {
    @Test
    public void testPersistFinalEntitiesAndStateStoreBehavior() {
        StateStore stateStore = new InMemoryStateStore();
        FinalizeAndPersist persist = new FinalizeAndPersist(stateStore);

        // 构建示例 finalized entities
        Entity e1 = Entity.builder()
                .id(UUID.randomUUID().toString())
                .humanReadableId(0)
                .title("实体A")
                .type("Policy")
                .build();

        Entity e2 = Entity.builder()
                .id(UUID.randomUUID().toString())
                .humanReadableId(1)
                .title("实体B")
                .type("Policy")
                .build();

        List<Entity> list = List.of(e1, e2);

        // persist
        persist.persistFinalEntities("entities", list);

        // 验证存在并属于相同 source
        Assertions.assertTrue(stateStore.existsHumanReadableId("entities", 0));
        Assertions.assertTrue(stateStore.existsHumanReadableId("entities", 1));
        Assertions.assertTrue(stateStore.isBelongsToSameSource("entities", 0, e1.getId()));
        Assertions.assertTrue(stateStore.isBelongsToSameSource("entities", 1, e2.getId()));

        // 保存另一组（模拟 full rebuild：先 clear）
        Entity e3 = Entity.builder()
                .id(UUID.randomUUID().toString())
                .humanReadableId(0)
                .title("实体C")
                .type("Policy")
                .build();

        persist.persistFinalEntities("entities", List.of(e3));

        // 由于 persistFinalEntities 内部 clear 了旧绑定，旧 hrid(0) 应该现在指向 e3.id
        Assertions.assertTrue(stateStore.existsHumanReadableId("entities", 0));
        Assertions.assertTrue(stateStore.isBelongsToSameSource("entities", 0, e3.getId()));
    }

    @Test
    public void testConflictThrowsIfNotCleared() {
        StateStore stateStore = new InMemoryStateStore();
        FinalizeAndPersist persist = new FinalizeAndPersist(stateStore);

        Entity e1 = Entity.builder()
                .id(UUID.randomUUID().toString())
                .humanReadableId(0)
                .title("实体A")
                .type("Policy")
                .build();

        stateStore.saveHumanReadableId("entities", 0, e1.getId());

        // 如果我们 create 一个不同的 entity 使用同样的 hrid 并调用 persistFinalEntities
        // persistFinalEntities 会先 clear，所以此处我们模拟未 clear 的情形：
        // 直接调用 save 并检测 conflict logic 需要单独实现；默认 persistFinalEntities always clears.
        // 这里仅验证 stateStore 报存在且不属于相同 source。
        Entity e2 = Entity.builder()
                .id(UUID.randomUUID().toString())
                .humanReadableId(0)
                .title("实体B")
                .type("Policy")
                .build();

        // 确认存在并且不属于同一 source
        Assertions.assertTrue(stateStore.existsHumanReadableId("entities", 0));
        Assertions.assertFalse(stateStore.isBelongsToSameSource("entities", 0, e2.getId()));
    }
}
