package com.gdin.inspection.graphrag.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class ModelRoundTripTest {
    @Resource
    private ResourceLoader resourceLoader;

    private final ObjectMapper mapper = IOUtil.mapper();

    @Test
    public void testEntityDeserializationRoundTrip() throws Exception {
        try (InputStream is = resourceLoader.getResource("classpath:sample-entity.json").getInputStream()) {
            assertNotNull(is, "sample-entity.json not found in resources");
            Entity e = mapper.readValue(is, Entity.class);
            assertEquals("ent-sample-001", e.getId());
            assertEquals(Integer.valueOf(0), e.getHumanReadableId());
            assertEquals("城市规划法", e.getTitle());
            // roundtrip
            String json = mapper.writeValueAsString(e);
            Entity e2 = mapper.readValue(json, Entity.class);
            assertEquals(e.getId(), e2.getId());
            assertEquals(e.getHumanReadableId(), e2.getHumanReadableId());
        }
    }

    @Test
    public void testTextUnitDeserializationRoundTrip() throws Exception {
        try (InputStream is = resourceLoader.getResource("classpath:sample-textunit.json").getInputStream()) {
            assertNotNull(is, "sample-textunit.json not found in resources");
            TextUnit t = mapper.readValue(is, TextUnit.class);
            assertEquals("doc-001_unit-0001", t.getId());
            assertEquals(Integer.valueOf(0), t.getHumanReadableId());
            String json = mapper.writeValueAsString(t);
            TextUnit t2 = mapper.readValue(json, TextUnit.class);
            assertEquals(t.getText(), t2.getText());
            assertEquals(t.getHumanReadableId(), t2.getHumanReadableId());
        }
    }
}
