package com.gdin.inspection.graphrag.v2;

import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.query.basic.BasicSearch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class QueryTest {
    @Resource
    private BasicSearch basicSearch;

    @Test
    void basicSearchTest() throws InterruptedException {
        BasicSearch.BasicStreamResult r = basicSearch.streamSearch(
                "陪产假和看护假的区别是什么？",
                10,
                12_000,
                null
        );

        String answer = SseUtil.getResponseWithoutThink(null, r.tokenStream(), r.memoryId());
        log.info(answer);
    }
}
