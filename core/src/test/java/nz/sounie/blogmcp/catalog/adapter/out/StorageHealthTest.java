package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.catalog.application.SyncMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageHealthTest {

  @Test
  @DisplayName("AC-APP-27: a healthy store starts with an incremental sync")
  void healthy_store_starts_incremental() {
    assertThat(StorageHealth.HEALTHY.startupSyncMode()).isEqualTo(SyncMode.INCREMENTAL);
  }

  @Test
  @DisplayName(
      "AC-APP-27: a damaged store starts with a reconcile, so lost posts are fetched again")
  void damaged_store_starts_with_a_reconcile() {
    assertThat(StorageHealth.DAMAGED.startupSyncMode()).isEqualTo(SyncMode.RECONCILE);
  }
}
