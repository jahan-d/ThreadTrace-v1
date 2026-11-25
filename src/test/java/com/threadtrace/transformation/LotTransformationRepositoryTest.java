package com.threadtrace.transformation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LotTransformationRepositoryTest {

    @Autowired
    private LotTransformationRepository transformationRepository;

    @Test
    @DisplayName("acquireTransactionAdvisoryLock should execute native pg_advisory_xact_lock without error")
    void shouldAcquireTransactionAdvisoryLockSuccessfully() {
        long testLockKey = 4287941209871625412L;

        assertDoesNotThrow(() ->
                transformationRepository.acquireTransactionAdvisoryLock(testLockKey)
        );
    }
}
