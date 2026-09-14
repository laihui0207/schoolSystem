package com.school.module.term.service;

import com.school.AbstractPostgresIT;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AcademicTermServiceTest extends AbstractPostgresIT {

    @Autowired AcademicTermService termService;

    @BeforeEach void setUp() { TestAuth.asSchoolAdmin(1L); }
    @AfterEach  void tearDown() { TestAuth.clear(); }

    @Test
    void createAndQueryTerm() {
        AcademicTermCreateRequest req = new AcademicTermCreateRequest();
        req.setSchoolId(1L);
        req.setYear("2025-2026");
        req.setTerm(1);
        AcademicTermResponse created = termService.create(req);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getYear()).isEqualTo("2025-2026");

        AcademicTermQueryRequest query = new AcademicTermQueryRequest();
        query.setSchoolId(1L);
        var page = termService.queryTerms(query);
        assertThat(page.getRecords()).isNotEmpty();
    }
}
