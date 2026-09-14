package com.school.module.subject.service;

import com.school.AbstractPostgresIT;
import com.school.module.subject.dto.SubjectCreateRequest;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
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
class SubjectServiceTest extends AbstractPostgresIT {

    @Autowired SubjectService subjectService;

    @BeforeEach void setUp() { TestAuth.asSchoolAdmin(1L); }
    @AfterEach  void tearDown() { TestAuth.clear(); }

    @Test
    void createAndQuerySubject() {
        SubjectCreateRequest req = new SubjectCreateRequest();
        req.setSchoolId(1L);
        req.setSubjectName("数学");
        req.setSubjectCode("MATH");
        SubjectResponse created = subjectService.create(req);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getSubjectName()).isEqualTo("数学");

        SubjectQueryRequest query = new SubjectQueryRequest();
        query.setSchoolId(1L);
        query.setSubjectName("数学");
        var page = subjectService.querySubjects(query);
        assertThat(page.getRecords()).isNotEmpty();
    }
}
