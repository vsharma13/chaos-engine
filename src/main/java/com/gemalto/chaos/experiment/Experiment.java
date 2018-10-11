package com.gemalto.chaos.experiment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.admin.AdminManager;
import com.gemalto.chaos.constants.AttackConstants;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.experiment.enums.ExperimentState;
import com.gemalto.chaos.experiment.enums.ExperimentType;
import com.gemalto.chaos.notification.ChaosEvent;
import com.gemalto.chaos.notification.NotificationManager;
import com.gemalto.chaos.notification.enums.NotificationLevel;
import com.gemalto.chaos.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gemalto.chaos.constants.AttackConstants.DEFAULT_TIME_BEFORE_FINALIZATION_SECONDS;
import static com.gemalto.chaos.util.MethodUtils.getMethodsWithAnnotation;
import static java.util.UUID.randomUUID;

public abstract class Experiment {
    private static final Logger log = LoggerFactory.getLogger(Experiment.class);
    private final String id = randomUUID().toString();
    protected Container container;
    protected ExperimentType experimentType;
    protected Duration duration = Duration.ofMinutes(AttackConstants.DEFAULT_ATTACK_DURATION_MINUTES);
    protected Duration finalizationDuration = Duration.ofSeconds(DEFAULT_TIME_BEFORE_FINALIZATION_SECONDS);
    private Platform experimentLayer;
    private Method experimentMethod;
    private ExperimentState experimentState = ExperimentState.NOT_YET_STARTED;
    private transient NotificationManager notificationManager;
    private Callable<Void> selfHealingMethod = () -> null;
    private Callable<ContainerHealth> checkContainerHealth;
    private Callable<Void> finalizeMethod;
    private Instant startTime = Instant.now();
    private Instant finalizationStartTime;
    private Instant lastSelfHealingTime;
    private AtomicInteger selfHealingCounter = new AtomicInteger(0);

    public Platform getExperimentLayer () {
        return experimentLayer;
    }

    private void setExperimentLayer (Platform experimentLayer) {
        this.experimentLayer = experimentLayer;
    }

    @JsonIgnore
    public Method getExperimentMethod () {
        return experimentMethod;
    }

    private void setExperimentMethod (Method experimentMethod) {
        this.experimentMethod = experimentMethod;
    }

    public Callable<Void> getSelfHealingMethod () {
        return selfHealingMethod;
    }

    public Callable<Void> getFinalizeMethod () {
        return finalizeMethod;
    }

    public void setSelfHealingMethod (Callable<Void> selfHealingMethod) {
        this.selfHealingMethod = selfHealingMethod;
    }

    public void setFinalizeMethod (Callable<Void> finalizeMethod) {
        this.finalizeMethod = finalizeMethod;
    }

    public Callable<ContainerHealth> getCheckContainerHealth () {
        return checkContainerHealth;
    }

    public void setCheckContainerHealth (Callable<ContainerHealth> checkContainerHealth) {
        this.checkContainerHealth = checkContainerHealth;
    }

    public void setFinalizationDuration (Duration finalizationDuration) {
        this.finalizationDuration = finalizationDuration;
    }

    public String getId () {
        return id;
    }

    public Instant getStartTime () {
        return startTime;
    }

    public Container getContainer () {
        return container;
    }

    boolean startExperiment (NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
        if (!AdminManager.canRunExperiments()) {
            log.info("Cannot start experiments right now, system is {}", AdminManager.getAdminState());
            return false;
        }
        if (container.getContainerHealth(experimentType) != ContainerHealth.NORMAL) {
            log.info("Failed to start an experiment as this container is already in an abnormal state\n{}", container);
            return false;
        }
        if (container.supportsAttackType(experimentType)) {
            List<Method> experimentMethods = getMethodsWithAnnotation(container.getClass(), getExperimentType().getAnnotation());
            if (experimentMethods.isEmpty()) {
                throw new ChaosException("Could not find an experiment vector");
            }
            int index = ThreadLocalRandom.current().nextInt(experimentMethods.size());
            setExperimentMethod(experimentMethods.get(index));
            setExperimentLayer(container.getPlatform());
            notificationManager.sendNotification(ChaosEvent.builder()
                                                           .fromAttack(this)
                                                           .withNotificationLevel(NotificationLevel.WARN)
                                                           .withMessage("Starting new experiment")
                                                           .build());
            container.attackContainer(this);
            experimentState = ExperimentState.STARTED;
        }
        return true;
    }

    public ExperimentType getExperimentType () {
        return experimentType;
    }

    ExperimentState getExperimentState () {
        experimentState = checkExperimentState();
        return experimentState;
    }

    private synchronized ExperimentState checkExperimentState () {
        switch (checkContainerHealth()) {
            case NORMAL:
                if (isFinalizable()) {
                    log.info("Experiment {} complete", id);
                    notificationManager.sendNotification(ChaosEvent.builder()
                                                                   .fromAttack(this)
                                                                   .withNotificationLevel(NotificationLevel.GOOD)
                                                                   .withMessage("Experiment finished. Container recovered from the experiment")
                                                                   .build());
                    finalizeExperiment();
                    return ExperimentState.FINISHED;
                }
                return ExperimentState.STARTED;
            case DOES_NOT_EXIST:
                log.info("Experiment {} no longer maps to existing container", id);
                notificationManager.sendNotification(ChaosEvent.builder()
                                                               .fromAttack(this)
                                                               .withNotificationLevel(NotificationLevel.ERROR)
                                                               .withMessage("Container no longer exists.")
                                                               .build());
                return ExperimentState.FINISHED;
            case UNDER_ATTACK:
            default:
                doSelfHealing();
                return ExperimentState.STARTED;
        }
    }

    private ContainerHealth checkContainerHealth () {
        if (checkContainerHealth != null) {
            try {
                return checkContainerHealth.call();
            } catch (Exception e) {
                log.error("Issue while checking container health using specific method", e);
            }
        }
        return container.getContainerHealth(experimentType);
    }

    private boolean isOverDuration () {
        return Instant.now().isAfter(startTime.plus(duration));
    }

    private boolean isFinalizable () {
        if (finalizationStartTime == null) {
            finalizationStartTime = Instant.now();
        }
        boolean finalizable = Instant.now().isAfter(finalizationStartTime.plus(finalizationDuration));
        log.debug("Experiment {} is finalizable = {}", id, finalizable);
        return finalizable;
    }

    private void finalizeExperiment () {
        if (finalizeMethod != null) {
            try {
                log.info("Finalizing experiment {} on container {}", id, container.getSimpleName());
                finalizeMethod.call();
            } catch (Exception e) {
                log.error("Error while finalizing experiment {} on container {}", id, container.getSimpleName());
            }
        }
    }

    private void doSelfHealing () {
        if (isOverDuration()) {
            try {
                log.warn("The experiment {} has gone on too long, invoking self-healing. \n{}", id, this);
                ChaosEvent chaosEvent;
                if (canRunSelfHealing()) {
                    StringBuilder message = new StringBuilder();
                    message.append("The experiment has gone on too long, invoking self-healing.");
                    if (selfHealingCounter.incrementAndGet() > 1) {
                        message.append("This is self healing attempt number ").append(selfHealingCounter.get()).append(".");
                    }
                    chaosEvent = ChaosEvent.builder()
                                           .fromAttack(this)
                                           .withNotificationLevel(NotificationLevel.WARN)
                                           .withMessage(message.toString())
                                           .build();
                    callSelfHealing();
                } else if (AdminManager.canRunSelfHealing()) {
                    chaosEvent = ChaosEvent.builder()
                                           .fromAttack(this)
                                           .withNotificationLevel(NotificationLevel.WARN)
                                           .withMessage("Cannot run self healing again yet")
                                           .build();
                } else {
                    chaosEvent = ChaosEvent.builder()
                                           .fromAttack(this)
                                           .withNotificationLevel(NotificationLevel.WARN)
                                           .withMessage("System is paused and unable to run self healing")
                                           .build();
                }
                notificationManager.sendNotification(chaosEvent);
            } catch (ChaosException e) {
                log.error("Experiment {}: An exception occurred while running self-healing.", id, e);
                notificationManager.sendNotification(ChaosEvent.builder()
                                                               .fromAttack(this)
                                                               .withNotificationLevel(NotificationLevel.ERROR)
                                                               .withMessage("An exception occurred while running self-healing.")
                                                               .build());
            }
        } else {
            notificationManager.sendNotification(ChaosEvent.builder()
                                                           .fromAttack(this)
                                                           .withNotificationLevel(NotificationLevel.WARN)
                                                           .withMessage("Experiment not yet finished.")
                                                           .build());
        }
    }

    private boolean canRunSelfHealing () {
        boolean canRunSelfHealing = lastSelfHealingTime == null || lastSelfHealingTime.plus(getMinimumTimeBetweenSelfHealing())
                                                                                      .isBefore(Instant.now());
        return canRunSelfHealing && AdminManager.canRunSelfHealing();
    }

    private void callSelfHealing () {
        try {
            selfHealingMethod.call();
        } catch (Exception e) {
            throw new ChaosException("Exception while self healing.", e);
        } finally {
            lastSelfHealingTime = Instant.now();
        }
    }

    private Duration getMinimumTimeBetweenSelfHealing () {
        return container.getMinimumSelfHealingInterval();
    }
}