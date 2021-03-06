/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thales.chaos.calendar.HolidayManager;
import com.thales.chaos.constants.DataDogConstants;
import com.thales.chaos.container.Container;
import com.thales.chaos.experiment.ExperimentalObject;
import com.thales.chaos.platform.enums.ApiStatus;
import com.thales.chaos.platform.enums.PlatformHealth;
import com.thales.chaos.platform.enums.PlatformLevel;
import com.thales.chaos.scheduler.Scheduler;
import com.thales.chaos.scheduler.impl.ChaosScheduler;
import com.thales.chaos.util.Expiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.thales.chaos.constants.DataDogConstants.DATADOG_CONTAINER_KEY;
import static com.thales.chaos.constants.ExperimentConstants.DEFAULT_SELF_HEALING_INTERVAL_MINUTES;
import static java.util.stream.Collectors.toSet;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.v;

public abstract class Platform implements ExperimentalObject {
    private static final Duration ROSTER_CACHE_DURATION = Duration.ofHours(1);
    private static final double DEFAULT_PROBABILITY = 0.2D;
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private long averageMillisPerExperiment = 14400000L;
    private Scheduler scheduler;
    private Expiring<List<Container>> roster;
    private Set<Instant> experimentTimes = new HashSet<>();
    @Autowired
    @Lazy
    private HolidayManager holidayManager;

    public void setAverageMillisPerExperiment (long averageMillisPerExperiment) {
        if (averageMillisPerExperiment == this.averageMillisPerExperiment) return;
        log.info("Setting average time between failure for {} to {} ms", this, averageMillisPerExperiment);
        this.averageMillisPerExperiment = averageMillisPerExperiment;
        this.scheduler = null;
    }

    public void expireCachedRoster () {
        if (roster != null) roster.expire();
    }

    public abstract ApiStatus getApiStatus ();

    public abstract PlatformLevel getPlatformLevel ();

    public abstract PlatformHealth getPlatformHealth ();

    public double getDestructionProbability () {
        return DEFAULT_PROBABILITY;
    }

    public String getPlatformType () {
        return this.getClass().getSimpleName();
    }

    public synchronized List<Container> getRoster () {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(DataDogConstants.DATADOG_PLATFORM_KEY, this.getPlatformType())) {
            if (roster == null) {
                roster = new Expiring<>(generateRoster(), ROSTER_CACHE_DURATION);
            }
            return roster.computeIfAbsent(this::generateRoster, ROSTER_CACHE_DURATION);
        }
    }

    protected abstract List<Container> generateRoster ();

    public Collection<Container> getRosterByAggregationId (String aggregationId) {
        return getRoster().stream().filter(container -> container.getAggregationIdentifier().equals(aggregationId)).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public synchronized boolean canExperiment () {
        return getScheduler().getNextChaosTime().isBefore(Instant.now());
    }

    private Scheduler getScheduler () {
        if (scheduler == null) initScheduler();
        return scheduler;
    }

    private void initScheduler () {
        scheduler = ChaosScheduler.builder().withAverageMillisBetweenExperiments(averageMillisPerExperiment)
                                  .withHolidayManager(holidayManager)
                                  .build();
    }

    public Instant getNextChaosTime () {
        return getScheduler().getNextChaosTime();
    }

    public Platform scheduleExperiment () {
        expireCachedRoster();
        log.info("Scheduling an experiment on {}", keyValue(DataDogConstants.DATADOG_PLATFORM_KEY, this.getPlatformType()));
        getScheduler().startExperiment();
        experimentTimes.add(Instant.now());
        return this;
    }

    @SuppressWarnings("unused") // public get methods are consumed by Spring Rest.
    public Set<Date> getExperimentTimes () {
        // Instant to String conversion is done due to Swagger which does not support java.time classes - SCT-6607
        return experimentTimes.stream().map(Date::from).collect(toSet());
    }

    @JsonIgnore
    public Duration getMinimumSelfHealingInterval () {
        return Duration.ofMinutes(DEFAULT_SELF_HEALING_INTERVAL_MINUTES);
    }

    public List<Container> generateExperimentRoster () {
        Map<String, List<Container>> groupedContainers = getRoster().stream()
                                                                    .collect(Collectors.groupingBy(container -> Optional
                                                                            .of(container)
                                                                            .map(Container::getAggregationIdentifier)
                                                                            .orElse(getDefaultUngroupedAggregationIdentifier())));
        List<Container> ungrouped = groupedContainers.remove(getDefaultUngroupedAggregationIdentifier());
        List<Container> experimentContainers = new ArrayList<>(ungrouped != null ? ungrouped : Collections.emptyList());
        groupedContainers.forEach((k, v) -> {
            int size = v.size();
            if (size < 2) {
                log.warn("Group {} does not have redundancy. It is not protected from experiments.", k);
                experimentContainers.addAll(v);
                return;
            }
            int excludedElement = new Random().nextInt(size);
            Container removedContainer = v.remove(excludedElement);
            log.info("Protected container {} from experiment", v(DATADOG_CONTAINER_KEY, removedContainer));
            experimentContainers.addAll(v);
        });
        return experimentContainers;
    }

    protected String getDefaultUngroupedAggregationIdentifier () {
        return "ungrouped";
    }

    public abstract boolean isContainerRecycled (Container container);

    public boolean hasEligibleContainersForExperiments () {
        return getRoster().stream().anyMatch(Container::eligibleForExperiments);
    }

    public Container getContainerByIdentifier (String containerIdentifier) {
        return getRoster().stream()
                          .filter(container -> container.compareUniqueIdentifier(containerIdentifier))
                          .findFirst()
                          .orElse(null);
    }
}
