package com.gemalto.chaos.constants;

public class ExperimentConstants {
    public static final int DEFAULT_EXPERIMENT_DURATION_MINUTES = 5;
    public static final int DEFAULT_TIME_BEFORE_FINALIZATION_SECONDS = 30;
    public static final int DEFAULT_SELF_HEALING_INTERVAL_MINUTES = 5;
    public static final int DEFAULT_MAXIMUM_SELF_HEALING_RETRIES = 10;
    public static final String CANNOT_RUN_SELF_HEALING_AGAIN_YET = "Cannot run self healing again yet";
    public static final String SYSTEM_IS_PAUSED_AND_UNABLE_TO_RUN_SELF_HEALING = "System is paused and unable to run self healing";
    public static final String AN_EXCEPTION_OCCURRED_WHILE_RUNNING_SELF_HEALING = "An exception occurred while running self-healing.";
    public static final String THE_EXPERIMENT_HAS_GONE_ON_TOO_LONG_INVOKING_SELF_HEALING = "The experiment has gone on too long, invoking self-healing.";
    public static final String THIS_IS_SELF_HEALING_ATTEMPT_NUMBER = " This is self healing attempt number ";
    public static final String MAXIMUM_SELF_HEALING_RETRIES_REACHED= "Maximum self healing retries reached";
    public static final String STARTING_NEW_EXPERIMENT= "Starting new experiment";
    public static final String FAILED_TO_START_EXPERIMENT= "Failed to start experiment";
    public static final String EXPERIMENT_METHOD_NOT_SET_YET="Experiment method not set yet";
    public static final int DEFAULT_EXPERIMENT_MINIMUM_DURATION_SECONDS = 30;

    private ExperimentConstants () {
    }
}
