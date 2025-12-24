package com.gdin.inspection.graphrag.v2.query.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TableRecords {

    private final List<String> columns;
    private final List<List<String>> rows;

    public TableRecords(List<String> columns, List<List<String>> rows) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public List<String> getColumns() { return columns; }
    public List<List<String>> getRows() { return rows; }

    public int size() { return rows.size(); }
}

