package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.update.IncrementalTextUnits;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UpdateTextUnitsWorkflow {

    public List<TextUnit> run(List<TextUnit> oldTextUnits, List<TextUnit> deltaTextUnits, Map<String, String> entityIdMapping) {
        return IncrementalTextUnits.updateAndMergeTextUnits(oldTextUnits, deltaTextUnits, entityIdMapping);
    }
}