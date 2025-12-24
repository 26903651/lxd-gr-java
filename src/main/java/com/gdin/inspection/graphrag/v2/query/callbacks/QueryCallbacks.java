package com.gdin.inspection.graphrag.v2.query.callbacks;

import com.gdin.inspection.graphrag.v2.query.SearchResult;
import com.gdin.inspection.graphrag.v2.query.context.TableRecords;

import java.util.List;
import java.util.Map;

public interface QueryCallbacks {

    default void onContext(Object context) {}

    default void onMapResponseStart(List<String> mapResponseContexts) {}

    default void onMapResponseEnd(List<SearchResult> mapResponseOutputs) {}

    default void onReduceResponseStart(Object reduceResponseContext) {}

    default void onReduceResponseEnd(String reduceResponseOutput) {}

    default void onLlmNewToken(String token) {}

    /** 方便 basic search 直接传 Map<String, TableRecords> */
    default void onContextRecords(Map<String, TableRecords> contextRecords) {
        onContext(contextRecords);
    }
}
