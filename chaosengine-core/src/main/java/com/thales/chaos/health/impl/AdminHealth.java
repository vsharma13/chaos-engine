package com.thales.chaos.health.impl;

import com.thales.chaos.admin.AdminManager;
import com.thales.chaos.admin.enums.AdminState;
import com.thales.chaos.health.SystemHealth;
import com.thales.chaos.health.enums.SystemHealthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(AdminManager.class)
public class AdminHealth implements SystemHealth {
    private static final Logger log = LoggerFactory.getLogger(AdminHealth.class);
    @Autowired
    private AdminManager adminManager;

    @Autowired
    AdminHealth () {
        log.debug("Using Administrative State for health check");
    }

    @Override
    public SystemHealthState getHealth () {
        boolean healthyState = AdminState.getHealthyStates().contains(adminManager.getAdminState());
        boolean longTimeInState = adminManager.getTimeInState().getSeconds() > 60 * 5;
        return (!healthyState) && longTimeInState ? SystemHealthState.ERROR : SystemHealthState.OK;
    }
}