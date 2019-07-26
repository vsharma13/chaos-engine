package com.thales.chaos.container.impl;

import com.thales.chaos.constants.AwsEC2Constants;
import com.thales.chaos.constants.DataDogConstants;
import com.thales.chaos.container.AwsContainer;
import com.thales.chaos.container.annotations.Identifier;
import com.thales.chaos.container.enums.ContainerHealth;
import com.thales.chaos.exception.ChaosException;
import com.thales.chaos.experiment.Experiment;
import com.thales.chaos.experiment.annotations.CattleExperiment;
import com.thales.chaos.experiment.annotations.NetworkExperiment;
import com.thales.chaos.experiment.annotations.StateExperiment;
import com.thales.chaos.experiment.enums.ExperimentType;
import com.thales.chaos.notification.datadog.DataDogIdentifier;
import com.thales.chaos.platform.Platform;
import com.thales.chaos.platform.impl.AwsEC2Platform;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.thales.chaos.constants.AwsEC2Constants.AWS_EC2_HARD_REBOOT_TIMER_MINUTES;
import static com.thales.chaos.exception.enums.AwsChaosErrorCode.NOT_PART_OF_ASG;
import static com.thales.chaos.notification.datadog.DataDogIdentifier.dataDogIdentifier;
import static java.util.function.Predicate.not;
import static net.logstash.logback.argument.StructuredArguments.v;

public class AwsEC2Container extends AwsContainer {
    @Identifier(order = 0)
    private String instanceId;
    @Identifier(order = 1)
    private String keyName;
    @Identifier(order = 2)
    private String name;
    @Identifier(order = 3)
    private String publicAddress;
    @Identifier(order = 4)
    private String privateAddress;
    @Identifier(order = 5)
    private String imageId;
    @Identifier(order = 6)
    private String groupIdentifier = AwsEC2Constants.NO_GROUPING_IDENTIFIER;
    private boolean nativeAwsAutoscaling = false;
    private AwsEC2Platform awsEC2Platform;
    private final Callable<Void> startContainerMethod = () -> {
        awsEC2Platform.startInstance(instanceId);
        return null;
    };
    private final Callable<ContainerHealth> checkContainerStartedMethod = () -> awsEC2Platform.checkHealth(instanceId);

    private AwsEC2Container () {
        super();
    }

    public static AwsEC2ContainerBuilder builder () {
        return AwsEC2ContainerBuilder.anAwsEC2Container();
    }

    public String getInstanceId () {
        return instanceId;
    }

    public String getGroupIdentifier () {
        return groupIdentifier;
    }

    public String getKeyName () {
        return keyName;
    }

    public String getName () {
        return name;
    }

    public boolean isSSHCapable () {
        return supportsShellBasedExperiments();
    }

    public String getImageId () {
        return imageId;
    }

    @Override
    public Platform getPlatform () {
        return awsEC2Platform;
    }

    @Override
    protected ContainerHealth updateContainerHealthImpl (ExperimentType experimentType) {
        return awsEC2Platform.checkHealth(instanceId);
    }

    @Override
    public boolean eligibleForExperiments () {
        if (isStarted()) {
            return true;
        }
        log.warn("Ignoring {} because it is not in a running state", v(DataDogConstants.DATADOG_CONTAINER_KEY, this));
        return false;
    }

    boolean isStarted () {
        return awsEC2Platform.isStarted(this);
    }

    @Override
    public String getSimpleName () {
        return String.format("%s (%s) [%s]", name, keyName, instanceId);
    }

    @Override
    public String getAggregationIdentifier () {
        return Stream.of(groupIdentifier, name)
                     .filter(Objects::nonNull)
                     .filter(not(String::isBlank))
                     .filter(not(AwsEC2Constants.NO_GROUPING_IDENTIFIER::equals))
                     .findFirst()
                     .orElse(instanceId);
    }

    @Override
    public DataDogIdentifier getDataDogIdentifier () {
        return dataDogIdentifier().withValue(instanceId);
    }

    @Override
    protected boolean compareUniqueIdentifierInner (@NotNull String uniqueIdentifier) {
        return uniqueIdentifier.equals(instanceId);
    }

    @Override
    public boolean isCattle () {
        return isMemberOfScaledGroup();
    }

    @Override
    public boolean supportsShellBasedExperiments () {
        String routableAddress = getRoutableAddress();
        return super.supportsShellBasedExperiments() && routableAddress != null && !routableAddress.isBlank() && awsEC2Platform
                .hasKey(keyName);
    }

    boolean isMemberOfScaledGroup () {
        return !AwsEC2Constants.NO_GROUPING_IDENTIFIER.equals(groupIdentifier);
    }

    @StateExperiment
    public void stopContainer (Experiment experiment) {
        awsEC2Platform.stopInstance(instanceId);
        experiment.setSelfHealingMethod(autoscalingSelfHealingWrapper(startContainerMethod));
        experiment.setCheckContainerHealth(autoscalingHealthcheckWrapper(checkContainerStartedMethod));
    }

    Callable<Void> autoscalingSelfHealingWrapper (@NotNull Callable<Void> baseMethod) {
        final AtomicBoolean asgSelfHealingRun = new AtomicBoolean(false);
        return isNativeAwsAutoscaling() ? () -> {
            if (asgSelfHealingRun.compareAndSet(false, true)) {
                log.debug("First self healing attempt will use Autoscaling to recreate");
                awsEC2Platform.triggerAutoscalingUnhealthy(instanceId);
                return null;
            }
            baseMethod.call();
            return null;
        } : baseMethod;
    }

    Callable<ContainerHealth> autoscalingHealthcheckWrapper (@NotNull Callable<ContainerHealth> baseMethod) {
        return isMemberOfScaledGroup() ? () -> {
            if (awsEC2Platform.isContainerTerminated(instanceId)) {
                if (isNativeAwsAutoscaling()) {
                    if (awsEC2Platform.isAutoscalingGroupAtDesiredInstances(groupIdentifier))
                        return ContainerHealth.NORMAL;
                    else {
                        log.info("Instance is terminated but scaling group is not at desired capacity");
                        return ContainerHealth.RUNNING_EXPERIMENT;
                    }
                }
                return ContainerHealth.NORMAL;
            }
            return baseMethod.call();
        } : baseMethod;
    }

    Boolean isNativeAwsAutoscaling () {
        return nativeAwsAutoscaling;
    }

    @StateExperiment
    public void restartContainer (Experiment experiment) {
        final Instant hardRebootTimer = Instant.now().plus(Duration.ofMinutes(AWS_EC2_HARD_REBOOT_TIMER_MINUTES));
        awsEC2Platform.restartInstance(instanceId);
        experiment.setSelfHealingMethod(autoscalingSelfHealingWrapper(startContainerMethod));
        experiment.setCheckContainerHealth(autoscalingHealthcheckWrapper(() -> hardRebootTimer.isBefore(Instant.now()) ? awsEC2Platform
                .checkHealth(instanceId) : ContainerHealth.RUNNING_EXPERIMENT));
        // If Ctrl+Alt+Del is disabled in the AMI, then it takes 4 minutes for EC2 to initiate a hard reboot.
    }

    @StateExperiment
    @CattleExperiment
    public void terminateASGContainer (Experiment experiment) {
        if (!isNativeAwsAutoscaling()) {
            log.debug("Instance {} is not part of an autoscaling group, won't terminate it.", v(DataDogConstants.EC2_INSTANCE, instanceId));
            throw new ChaosException(NOT_PART_OF_ASG);
        }
        awsEC2Platform.terminateInstance(instanceId);
        experiment.setCheckContainerHealth(autoscalingHealthcheckWrapper(() -> ContainerHealth.RUNNING_EXPERIMENT));
        experiment.setSelfHealingMethod(autoscalingSelfHealingWrapper(() -> null));
    }

    @NetworkExperiment
    public void removeSecurityGroups (Experiment experiment) {
        List<String> originalSecurityGroupIds = awsEC2Platform.getSecurityGroupIds(instanceId);
        awsEC2Platform.setSecurityGroupIds(instanceId, Collections.singletonList(awsEC2Platform.getChaosSecurityGroupForInstance(instanceId)));
        experiment.setCheckContainerHealth(autoscalingHealthcheckWrapper(() -> awsEC2Platform.verifySecurityGroupIds(instanceId, originalSecurityGroupIds)));
        experiment.setSelfHealingMethod(autoscalingSelfHealingWrapper(() -> {
            awsEC2Platform.setSecurityGroupIds(instanceId, originalSecurityGroupIds);
            return null;
        }));
    }

    public String getRoutableAddress () {
        return (getPrivateAddress() != null && awsEC2Platform.isAddressRoutable(getPrivateAddress())) ? getPrivateAddress() : getPublicAddress();
    }

    String getPrivateAddress () {
        return privateAddress;
    }

    public String getPublicAddress () {
        return publicAddress;
    }

    public static final class AwsEC2ContainerBuilder {
        private final Map<String, String> dataDogTags = new HashMap<>();
        private String instanceId;
        private String keyName;
        private String name;
        private AwsEC2Platform awsEC2Platform;
        private String availabilityZone;
        private String groupIdentifier = AwsEC2Constants.NO_GROUPING_IDENTIFIER;
        private boolean nativeAwsAutoscaling = false;
        private String publicAddress;
        private String privateAddress;
        private String imageId;

        private AwsEC2ContainerBuilder () {
        }

        static AwsEC2ContainerBuilder anAwsEC2Container () {
            return new AwsEC2ContainerBuilder();
        }

        public AwsEC2ContainerBuilder instanceId (String instanceId) {
            this.instanceId = instanceId;
            return this.withDataDogTag(DataDogConstants.DEFAULT_DATADOG_IDENTIFIER_KEY, instanceId);
        }

        public AwsEC2ContainerBuilder withDataDogTag (String key, String value) {
            dataDogTags.put(key, value);
            return this;
        }

        public AwsEC2ContainerBuilder keyName (String keyName) {
            this.keyName = keyName;
            return this;
        }

        public AwsEC2ContainerBuilder name (String name) {
            this.name = name;
            return this;
        }

        public AwsEC2ContainerBuilder awsEC2Platform (AwsEC2Platform awsEC2Platform) {
            this.awsEC2Platform = awsEC2Platform;
            return this;
        }

        public AwsEC2ContainerBuilder availabilityZone (String availabilityZone) {
            this.availabilityZone = availabilityZone;
            return this;
        }

        public AwsEC2ContainerBuilder groupIdentifier (String groupIdentifier) {
            this.groupIdentifier = groupIdentifier;
            return this;
        }

        public AwsEC2ContainerBuilder nativeAwsAutoscaling (boolean nativeAwsAutoscaling) {
            this.nativeAwsAutoscaling = nativeAwsAutoscaling;
            return this;
        }

        public AwsEC2ContainerBuilder publicAddress (String publicAddress) {
            this.publicAddress = publicAddress;
            return this;
        }

        public AwsEC2ContainerBuilder privateAddress (String privateAddress) {
            this.privateAddress = privateAddress;
            return this;
        }

        public AwsEC2ContainerBuilder imageId (String imageId) {
            this.imageId = imageId;
            return this;
        }

        public AwsEC2Container build () {
            AwsEC2Container awsEC2Container = new AwsEC2Container();
            awsEC2Container.awsEC2Platform = this.awsEC2Platform;
            awsEC2Container.instanceId = this.instanceId;
            awsEC2Container.keyName = this.keyName;
            awsEC2Container.name = this.name;
            awsEC2Container.availabilityZone = this.availabilityZone;
            awsEC2Container.groupIdentifier = this.groupIdentifier;
            awsEC2Container.nativeAwsAutoscaling = this.nativeAwsAutoscaling;
            awsEC2Container.publicAddress = this.publicAddress;
            awsEC2Container.privateAddress = this.privateAddress;
            awsEC2Container.imageId = this.imageId;
            awsEC2Container.dataDogTags.putAll(this.dataDogTags);
            try {
                awsEC2Container.setMappedDiagnosticContext();
                awsEC2Container.log.info("Created new AWS EC2 Container object");
            } finally {
                awsEC2Container.clearMappedDiagnosticContext();
            }
            return awsEC2Container;
        }
    }
}