package com.gdin.inspection.graphrag.v2;

import com.gdin.inspection.graphrag.v2.index.run.GraphRagIndexRunner;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class IndexTest {
    @Resource
    private GraphRagIndexRunner graphRagIndexRunner;

    @Test
    void testStandard() throws Exception {
        List<?> objects = graphRagIndexRunner.runStandard(List.of("9c3c1d6b-fd37-42b9-8b33-132da0beb6cd"));
        log.info("objects: {}", objects);
    }
}
