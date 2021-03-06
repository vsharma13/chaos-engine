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

package com.thales.chaos.container.impl;

import com.thales.chaos.container.enums.ContainerHealth;
import com.thales.chaos.exception.ChaosException;
import com.thales.chaos.experiment.Experiment;
import com.thales.chaos.experiment.enums.ExperimentType;
import com.thales.chaos.notification.datadog.DataDogIdentifier;
import com.thales.chaos.platform.impl.CloudFoundryApplicationPlatform;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.operations.applications.RestageApplicationRequest;
import org.cloudfoundry.operations.domains.Domain;
import org.cloudfoundry.operations.domains.Status;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class CloudFoundryApplicationTest {
    private static final String applicationId = UUID.randomUUID().toString();
    private static final int instance = new Random().nextInt(100);
    private static final String name = UUID.randomUUID().toString();
    @Mock
    private Experiment experiment;
    @MockBean
    private CloudFoundryApplicationPlatform cloudFoundryApplicationPlatform;
    private CloudFoundryApplication cloudFoundryApplication;

    @Before
    public void setUp () {
        cloudFoundryApplication = CloudFoundryApplication.builder()
                                                         .applicationID(applicationId)
                                                         .containerInstances(instance)
                                                         .name(name).applicationRoutes(getRouteList())
                                                         .platform(cloudFoundryApplicationPlatform)
                                                         .build();
        doAnswer(invocationOnMock -> {
            Object[] args = invocationOnMock.getArguments();
            doReturn(args[0]).when(experiment).getCheckContainerHealth();
            return null;
        }).when(experiment).setCheckContainerHealth(any());
        doAnswer(invocationOnMock -> {
            Object[] args = invocationOnMock.getArguments();
            doReturn(args[0]).when(experiment).getSelfHealingMethod();
            return null;
        }).when(experiment).setSelfHealingMethod(any());
        doAnswer(invocationOnMock -> {
            Object[] args = invocationOnMock.getArguments();
            doReturn(args[0]).when(experiment).getFinalizeMethod();
            return null;
        }).when(experiment).setFinalizeMethod(any());
    }

    private List<CloudFoundryApplicationRoute> getRouteList () {
        Domain httpDomain = Domain.builder().id("httpDomain").name("http.domain.com").type("").status(Status.SHARED).build();
        RouteEntity httpRouteEntity = RouteEntity.builder().host("httpHost").domainId(httpDomain.getId()).build();
        List<CloudFoundryApplicationRoute> cloudFoundryApplicationRoutes = new ArrayList<>();
        CloudFoundryApplicationRoute route = CloudFoundryApplicationRoute.builder()
                                                                         .route(httpRouteEntity)
                                                                         .domain(httpDomain)
                                                                         .applicationName(name)
                                                                         .build();
        cloudFoundryApplicationRoutes.add(route);
        return cloudFoundryApplicationRoutes;
    }

    @Test
    public void getAggegationIdentifier () {
        assertEquals(name, cloudFoundryApplication.getAggregationIdentifier());
    }

    @Test
    public void supportsShellBasedExperiments () {
        assertFalse(cloudFoundryApplication.supportsShellBasedExperiments());
    }

    @Test
    public void scaleApplicationHealing () throws Exception {
        cloudFoundryApplication.scaleApplication(experiment);
        verify(cloudFoundryApplicationPlatform, times(1)).rescaleApplication(eq(name), any(Integer.class));
        experiment.getSelfHealingMethod().run();
        assertEquals(cloudFoundryApplication.getOriginalContainerInstances(), cloudFoundryApplication.getActualContainerInstances());
    }

    @Test
    public void scaleApplicationFinalization () throws Exception {
        cloudFoundryApplication.scaleApplication(experiment);
        Mockito.verify(cloudFoundryApplicationPlatform, times(1)).rescaleApplication(eq(name), any(Integer.class));
        experiment.getFinalizeMethod().run();
        assertEquals(cloudFoundryApplication.getOriginalContainerInstances(), cloudFoundryApplication.getActualContainerInstances());
    }

    @Test
    public void scaleApplication () {
        cloudFoundryApplication.scaleApplication(experiment);
        verify(experiment, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(experiment, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        verify(experiment, times(1)).setFinalizeMethod(ArgumentMatchers.any());
        Mockito.verify(cloudFoundryApplicationPlatform, times(1)).rescaleApplication(eq(name), any(Integer.class));
    }

    @Test
    public void restageApplication () throws Exception {
        RestageApplicationRequest restageApplicationRequest = RestageApplicationRequest.builder().name(name).build();
        cloudFoundryApplication.restageApplication(experiment);
        verify(experiment, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(experiment, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(cloudFoundryApplicationPlatform, times(1)).restageApplication(restageApplicationRequest);
        experiment.getSelfHealingMethod().run();
    }

    @Test
    public void restartApplication () throws Exception {
        cloudFoundryApplication.restartApplication(experiment);
        verify(experiment, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(experiment, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(cloudFoundryApplicationPlatform, times(1)).restartApplication(name);
        experiment.getSelfHealingMethod().run();
    }

    @Test
    public void unmapRoute () throws Exception {
        cloudFoundryApplication.unmapRoute(experiment);
        verify(experiment, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(experiment, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(cloudFoundryApplicationPlatform, times(1)).unmapRoute(ArgumentMatchers.any());
        experiment.getSelfHealingMethod().run();
    }

    @Test(expected = ChaosException.class)
    public void unmapRouteAppWithNoRoutes () throws Exception {
        CloudFoundryApplication appNoRoutes = CloudFoundryApplication.builder()
                                                                     .applicationID(applicationId)
                                                                     .containerInstances(instance)
                                                                     .name(name)
                                                                     .applicationRoutes(new ArrayList<>())
                                                                     .platform(cloudFoundryApplicationPlatform)
                                                                     .build();
        appNoRoutes.unmapRoute(experiment);
    }

    @Test
    public void createExperiment () {
        Experiment experiment = cloudFoundryApplication.createExperiment();
        assertEquals(cloudFoundryApplication, experiment.getContainer());
    }

    @Test
    public void updateContainerHealthImpl () {
        doReturn(ContainerHealth.NORMAL).when(cloudFoundryApplicationPlatform).checkPlatformHealth();
        assertEquals(ContainerHealth.NORMAL, cloudFoundryApplication.updateContainerHealthImpl(ExperimentType.STATE));
    }

    @Test
    public void getPlatform () {
        assertEquals(cloudFoundryApplicationPlatform, cloudFoundryApplication.getPlatform());
    }

    @Test
    public void getApplicationID () {
        assertEquals(applicationId, cloudFoundryApplication.getApplicationID());
    }

    @Test
    public void getSimpleName () {
        assertEquals(name, cloudFoundryApplication.getSimpleName());
    }

    @Test
    public void getDataDogIdentifier () {
        assertEquals(DataDogIdentifier.dataDogIdentifier().withKey("application").withValue(name), cloudFoundryApplication.getDataDogIdentifier());
    }
}