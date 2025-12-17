package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.update.IncrementalCovariates;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UpdateCovariatesWorkflow {

    public List<Covariate> run(List<Covariate> oldCovariates, List<Covariate> deltaCovariates) {
        return IncrementalCovariates.mergeCovariates(oldCovariates, deltaCovariates);
    }
}
