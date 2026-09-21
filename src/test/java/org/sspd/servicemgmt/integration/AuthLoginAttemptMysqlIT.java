package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.sspd.servicemgmt.authoption.LoginAttemptService;
import org.sspd.servicemgmt.authoption.LoginAttemptStateRepository;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
class AuthLoginAttemptMysqlIT extends AbstractMysqlIntegrationTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LoginAttemptStateRepository loginAttemptStateRepository;

    @Test
    void concurrentFirstFailuresDoNotThrowDuplicateKeyErrors() throws Exception {
        String key = "it-concurrent-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> loginAttemptService.recordFailure(key));
        }

        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<Boolean> future : futures) {
            Boolean locked = future.get();
            assertTrue(locked != null);
        }
        assertTrue(loginAttemptStateRepository.findByLoginKey(key).isPresent());
    }
}
