package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;

/** The in-memory fake honours the same port contract as the file repository. */
class InMemorySyncCheckpointRepositoryTest extends SyncCheckpointRepositoryContract {

  @Override
  protected SyncCheckpointRepository newRepository() {
    return new InMemorySyncCheckpointRepository();
  }
}
