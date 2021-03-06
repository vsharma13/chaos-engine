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

package com.thales.chaos.notification.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thales.chaos.container.Container;
import com.thales.chaos.experiment.Experiment;
import com.thales.chaos.experiment.enums.ExperimentType;
import com.thales.chaos.notification.ChaosNotification;
import com.thales.chaos.notification.enums.NotificationLevel;

import java.util.Date;

@SuppressWarnings("unused")
public class ChaosExperimentEvent implements ChaosNotification {
    @JsonProperty
    private Container targetContainer;
    @JsonProperty
    private Date chaosTime;
    @JsonProperty
    private String experimentId;
    private String title;
    private String message;
    @JsonProperty
    private ExperimentType experimentType;
    @JsonProperty
    private String experimentMethod;
    private NotificationLevel notificationLevel;
    public static final String CHAOS_EXPERIMENT_EVENT_PREFIX = "Chaos Experiment Event";

    public static ChaosEventBuilder builder () {
        return ChaosEventBuilder.builder();
    }

    @Override
    public String getMessage () {
        return message;
    }

    public Container getTargetContainer () {
        return targetContainer;
    }

    @Override
    public String getTitle () {
        return title;
    }

    @Override
    public NotificationLevel getNotificationLevel () {
        return notificationLevel;
    }

    @Override
    public int hashCode () {
        int result = targetContainer != null ? targetContainer.hashCode() : 0;
        result = 31 * result + (chaosTime != null ? chaosTime.hashCode() : 0);
        result = 31 * result + (experimentId != null ? experimentId.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (experimentType != null ? experimentType.hashCode() : 0);
        result = 31 * result + (experimentMethod != null ? experimentMethod.hashCode() : 0);
        result = 31 * result + (notificationLevel != null ? notificationLevel.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChaosExperimentEvent that = (ChaosExperimentEvent) o;
        if (targetContainer != null ? !targetContainer.equals(that.targetContainer) : that.targetContainer != null)
            return false;
        if (chaosTime != null ? !chaosTime.equals(that.chaosTime) : that.chaosTime != null) return false;
        if (experimentId != null ? !experimentId.equals(that.experimentId) : that.experimentId != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (experimentType != that.experimentType) return false;
        if (experimentMethod != null ? !experimentMethod.equals(that.experimentMethod) : that.experimentMethod != null)
            return false;
        if (!title.equals(that.title)) return false;
        return notificationLevel == that.notificationLevel;
    }

    public static final class ChaosEventBuilder {
        private Container targetContainer;
        private Date chaosTime;
        private String experimentId;
        private String title = CHAOS_EXPERIMENT_EVENT_PREFIX;
        private String message;
        private ExperimentType experimentType;
        private String experimentMethod;
        private NotificationLevel notificationLevel;

        private ChaosEventBuilder () {
        }

        private static ChaosEventBuilder builder () {
            return new ChaosEventBuilder();
        }

        public ChaosEventBuilder fromExperiment (Experiment experiment) {
            this.chaosTime = Date.from(experiment.getStartTime());
            this.targetContainer = experiment.getContainer();
            this.experimentType = experiment.getExperimentType();
            this.experimentId = experiment.getId();
            this.experimentMethod = experiment.getExperimentMethod() != null ? experiment.getExperimentMethod().getExperimentName() : "";
            return this;
        }

        public ChaosEventBuilder withTargetContainer (Container targetContainer) {
            this.targetContainer = targetContainer;
            return this;
        }

        public ChaosEventBuilder withChaosTime (Date chaosTime) {
            this.chaosTime = chaosTime;
            return this;
        }

        public ChaosEventBuilder withTitle (String title) {
            this.title = CHAOS_EXPERIMENT_EVENT_PREFIX + " - " + title;
            return this;
        }

        public ChaosEventBuilder withMessage (String message) {
            this.message = message;
            return this;
        }

        public ChaosEventBuilder withExperimentType (ExperimentType experimentType) {
            this.experimentType = experimentType;
            return this;
        }

        public ChaosEventBuilder withNotificationLevel (NotificationLevel notificationLevel) {
            this.notificationLevel = notificationLevel;
            return this;
        }

        public ChaosEventBuilder withExperimentId (String experimentId) {
            this.experimentId = experimentId;
            return this;
        }

        public ChaosEventBuilder withExperimentMethod(String experimentMethod){
            this.experimentMethod = experimentMethod;
            return this;
        }

        public ChaosExperimentEvent build () {
            ChaosExperimentEvent chaosEvent = new ChaosExperimentEvent();
            chaosEvent.targetContainer = this.targetContainer;
            chaosEvent.title = this.title;
            chaosEvent.message = this.message;
            chaosEvent.chaosTime = this.chaosTime;
            chaosEvent.experimentId = this.experimentId;
            chaosEvent.experimentType = this.experimentType;
            chaosEvent.experimentMethod = this.experimentMethod;
            chaosEvent.notificationLevel = this.notificationLevel;
            return chaosEvent;
        }
    }
}
