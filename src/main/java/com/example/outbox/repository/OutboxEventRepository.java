package com.example.outbox.repository;

import com.example.outbox.entity.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            select * from outbox_events
            where processed_at is null
            order by created_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> findUnprocessedBatch(@Param("batchSize") int batchSize);

    long countByProcessedAtIsNull();

    @Query(value = """
            select created_at from outbox_events
            where processed_at is null
            order by created_at
            limit 1
            """, nativeQuery = true)
    Optional<Instant> findOldestUnprocessedCreatedAt();
}
