package com.school;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动测试
 *
 * @author School System
 */
@SpringBootTest
@ActiveProfiles("test")
class SchoolSystemApplicationTests {

    @Test
    void contextLoads() {
        // 测试Spring容器是否能正常启动
    }

}
