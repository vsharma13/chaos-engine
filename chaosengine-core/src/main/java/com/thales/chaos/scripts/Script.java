package com.thales.chaos.scripts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thales.chaos.experiment.enums.ExperimentType;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.Collection;

public interface Script {
    String getHealthCheckCommand ();

    String getSelfHealingCommand ();

    boolean isRequiresCattle ();

    String getScriptName ();

    boolean doesNotUseMissingDependencies (Collection<String> knownMissingDependencies);

    String getFinalizeCommand ();

    @JsonIgnore
    Resource getResource ();

    ExperimentType getExperimentType ();

    Collection<String> getDependencies ();

    Duration getMaximumDuration ();

    Duration getMinimumDuration ();
}
