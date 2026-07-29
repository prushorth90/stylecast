package com.stylecast.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Used directly by tests and seed-data verification; production code reads
 * inventory through the {@link ProductVariant#getInventoryRecords()}
 * association.
 */
public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, UUID> {

    List<InventoryRecord> findByProductVariantId(UUID productVariantId);
}
