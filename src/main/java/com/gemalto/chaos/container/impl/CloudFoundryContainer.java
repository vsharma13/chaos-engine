package com.gemalto.chaos.container.impl;

import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.experiment.Experiment;
import com.gemalto.chaos.experiment.annotations.StateExperiment;
import com.gemalto.chaos.experiment.enums.ExperimentType;
import com.gemalto.chaos.notification.datadog.DataDogIdentifier;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.platform.impl.CloudFoundryContainerPlatform;
import com.gemalto.chaos.ssh.ShellSessionCapability;
import com.gemalto.chaos.ssh.SshExperiment;
import com.gemalto.chaos.ssh.enums.ShellCommand;
import com.gemalto.chaos.ssh.impl.experiments.ForkBomb;
import com.gemalto.chaos.ssh.impl.experiments.RandomProcessTermination;
import org.cloudfoundry.operations.applications.RestageApplicationRequest;
import org.cloudfoundry.operations.applications.RestartApplicationInstanceRequest;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class CloudFoundryContainer extends Container {
    private String applicationId;
    private String name;
    private Integer instance;
    private transient List<ShellSessionCapability> detectedCapabilities;
    private transient CloudFoundryContainerPlatform cloudFoundryContainerPlatform;
    private transient Callable<Void> restageApplication = () -> {
        cloudFoundryContainerPlatform.restageApplication(getRestageApplicationRequest());
        return null;
    };
    private transient Callable<Void> restartContainer = () -> {
        cloudFoundryContainerPlatform.restartInstance(getRestartApplicationInstanceRequest());
        return null;
    };
    private transient Callable<ContainerHealth> isInstanceRunning = () -> cloudFoundryContainerPlatform.checkHealth(applicationId, instance);

    private CloudFoundryContainer () {
        super();
    }

    CloudFoundryContainer (String applicationId, String name, Integer instance) {
        super();
        this.applicationId = applicationId;
        this.name = name;
        this.instance = instance;
    }

    public static CloudFoundryContainerBuilder builder () {
        return CloudFoundryContainerBuilder.builder();
    }

    private Callable<ContainerHealth> sshHealthCheck(CloudFoundryContainer container,String command,int expectedExitStatus){
        return () -> {
            ContainerHealth instanceState = isInstanceRunning.call();
            ContainerHealth shellBasedHealthCheck = cloudFoundryContainerPlatform.sshBasedHealthCheck(container, command, expectedExitStatus);
            if (instanceState == ContainerHealth.NORMAL && shellBasedHealthCheck == ContainerHealth.NORMAL) {
                return ContainerHealth.NORMAL;
            }
            return ContainerHealth.RUNNING_EXPERIMENT;
        };
    }

    @StateExperiment
    public void forkBomb (Experiment experiment) {
        experiment.setSelfHealingMethod(restartContainer);
        String healthCheckCommand = ShellCommand.BINARYEXISTS + SshExperiment.DEFAULT_UPLOAD_PATH + ForkBomb.EXPERIMENT_SCRIPT;
        experiment.setCheckContainerHealth(sshHealthCheckInverse(this, healthCheckCommand, 0));
        cloudFoundryContainerPlatform.sshExperiment(new ForkBomb(), this);
    }

    private Callable<ContainerHealth> sshHealthCheckInverse (CloudFoundryContainer container, String command, int errorExitStatus) {
        return () -> {
            ContainerHealth instanceState = isInstanceRunning.call();
            ContainerHealth shellBasedHealthCheck = cloudFoundryContainerPlatform.sshBasedHealthCheckInverse(container, command, errorExitStatus);
            if (instanceState == ContainerHealth.NORMAL && shellBasedHealthCheck == ContainerHealth.NORMAL) {
                return ContainerHealth.NORMAL;
            }
            return ContainerHealth.RUNNING_EXPERIMENT;
        };
    }

    @Override
    public Platform getPlatform () {
        return cloudFoundryContainerPlatform;
    }

    @Override
    protected ContainerHealth updateContainerHealthImpl (ExperimentType experimentType) {
        return cloudFoundryContainerPlatform.checkHealth(applicationId, instance);
    }

    @Override
    public String getSimpleName () {
        return name + " - (" + instance + ")";
    }

    @Override
    public DataDogIdentifier getDataDogIdentifier () {
        return DataDogIdentifier.dataDogIdentifier().withValue(name + "-" + instance);
    }

    @Override
    protected boolean compareUniqueIdentifierInner (@NotNull String uniqueIdentifier) {
        return uniqueIdentifier.equals(name + "-" + instance);
    }

    @Override
    protected boolean isCattle () {
        return true;
    }

    @StateExperiment
    public void restartContainer (Experiment experiment) {
        experiment.setSelfHealingMethod(restageApplication);
        experiment.setCheckContainerHealth(isInstanceRunning);
        cloudFoundryContainerPlatform.restartInstance(getRestartApplicationInstanceRequest());
    }

    private RestartApplicationInstanceRequest getRestartApplicationInstanceRequest () {
        RestartApplicationInstanceRequest restartApplicationInstanceRequest = RestartApplicationInstanceRequest.builder()
                                                                                                               .name(name)
                                                                                                               .instanceIndex(instance)
                                                                                                               .build();
        log.info("{}", restartApplicationInstanceRequest);
        return restartApplicationInstanceRequest;
    }

    @StateExperiment
    public void terminateProcess (Experiment experiment) {
        experiment.setSelfHealingMethod(restartContainer);
        experiment.setCheckContainerHealth(isInstanceRunning); // TODO Real healthcheck
        cloudFoundryContainerPlatform.sshExperiment(new RandomProcessTermination(), this);
    }

    private RestageApplicationRequest getRestageApplicationRequest () {
        RestageApplicationRequest restageApplicationRequest = RestageApplicationRequest.builder().name(name).build();
        log.info("{}", restageApplicationRequest);
        return restageApplicationRequest;
    }

    public String getApplicationId () {
        return applicationId;
    }

    public String getName () {
        return name;
    }

    public Integer getInstance () {
        return instance;
    }

    public List<ShellSessionCapability> getDetectedCapabilities () {
        return detectedCapabilities;
    }

    public void setDetectedCapabilities (List<ShellSessionCapability> detectedCapabilities) {
        this.detectedCapabilities = detectedCapabilities;
    }

    public static final class CloudFoundryContainerBuilder {
        private final Map<String, String> dataDogTags = new HashMap<>();
        private String applicationId;
        private String name;
        private Integer instance;
        private CloudFoundryContainerPlatform cloudFoundryContainerPlatform;

        private CloudFoundryContainerBuilder () {
        }

        static CloudFoundryContainerBuilder builder () {
            return new CloudFoundryContainerBuilder();
        }

        public CloudFoundryContainerBuilder applicationId (String applicationId) {
            this.applicationId = applicationId;
            return dataDogTags("application_id", applicationId);
        }

        public CloudFoundryContainerBuilder dataDogTags (String key, String value) {
            this.dataDogTags.put(key, value);
            return this;
        }

        public CloudFoundryContainerBuilder name (String name) {
            this.name = name;
            return dataDogTags("application_name", name);
        }

        public CloudFoundryContainerBuilder platform (CloudFoundryContainerPlatform cloudFoundryContainerPlatform) {
            this.cloudFoundryContainerPlatform = cloudFoundryContainerPlatform;
            return this;
        }

        public CloudFoundryContainerBuilder instance (Integer instance) {
            this.instance = instance;
            return dataDogTags("instance_index", instance.toString());
        }

        public CloudFoundryContainer build () {
            CloudFoundryContainer cloudFoundryContainer = new CloudFoundryContainer();
            cloudFoundryContainer.name = this.name;
            cloudFoundryContainer.instance = this.instance;
            cloudFoundryContainer.applicationId = this.applicationId;
            cloudFoundryContainer.cloudFoundryContainerPlatform = this.cloudFoundryContainerPlatform;
            return cloudFoundryContainer;
        }
    }
}
