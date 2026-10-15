package com.store.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "APP_TEST_DATABASE_URL",
        matches = "^jdbc:postgresql://localhost:[0-9]+/fintrack_app_eval_[a-z0-9_]+$")
@SpringBootTest(properties = {
        "spring.datasource.url=${APP_TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DB_USER:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "mail.sync.enabled=false", "jwt.secret=context-tests-only-signing-key-at-least-32-bytes"
})
class AppTests {

    @Test
    void contextLoads() {
    }

}
