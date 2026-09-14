package com.school.module.school.controller;

import com.school.AbstractPostgresIT;
import com.school.module.school.service.SchoolService;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SchoolControllerTest extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolService schoolService;

    @BeforeEach void setUp() { TestAuth.asSuperAdmin(); }
    @AfterEach  void tearDown() { TestAuth.clear(); }

    @Test
    void listSchools_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/schools").contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createAndDeleteSchool() throws Exception {
        String body = """
            {"schoolName":"测试学校","schoolCode":"TEST001","address":"测试地址","phone":"12345678901"}
            """;
        var result = mockMvc.perform(post("/api/v1/schools")
                .contentType(MediaType.APPLICATION_JSON).content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.schoolName").value("测试学校"))
               .andReturn();

        // extract id and delete
        String resp = result.getResponse().getContentAsString();
        Long id = com.jayway.jsonpath.JsonPath.read(resp, "$.data.id");
        mockMvc.perform(delete("/api/v1/schools/" + id))
               .andExpect(status().isOk());
    }
}
