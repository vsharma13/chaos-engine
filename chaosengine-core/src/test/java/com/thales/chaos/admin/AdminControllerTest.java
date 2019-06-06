package com.thales.chaos.admin;

import com.thales.chaos.admin.enums.AdminState;
import com.thales.chaos.notification.NotificationManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.core.Is.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@WebMvcTest(controllers = AdminController.class)
public class AdminControllerTest {

    @MockBean
    private NotificationManager notificationManager;
    @SpyBean
    private AdminManager adminManager;
    @Autowired
    private MockMvc mvc;

    @Before
    public void beforeEach () {
        Mockito.doReturn(AdminState.STARTED).when(adminManager).getAdminState();
    }

    @Test
    public void getAdminState () throws Exception {
        mvc.perform(get("/admin" + "/state").contentType(APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", is("STARTED")));
    }

    @Test
    public void setAdminState () throws Exception {
        mvc.perform(post("/admin" + "/state").contentType(APPLICATION_JSON).param("state", "BOGUS STATE"))
           .andExpect(status().is4xxClientError());
        mvc.perform(post("/admin" + "/state").contentType(APPLICATION_JSON).param("state", "PAUSED"))
           .andExpect(status().isOk());
    }
}