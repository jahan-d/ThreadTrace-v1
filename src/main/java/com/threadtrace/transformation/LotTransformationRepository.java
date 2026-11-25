package com.threadtrace.transformation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotTransformationRepository
        extends JpaRepository<LotTransformation, Long> {

    /**
     * Acquires a PostgreSQL transaction-scoped advisory lock.
     * The lock is automatically released when the surrounding transaction commits or rolls back.
     *
     * @param lockKey 64-bit integer lock key representing the domain or resource lock
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquireTransactionAdvisoryLock(@Param("lockKey") long lockKey);
}
