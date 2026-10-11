package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.domain.post.PostRepository;

/** The in-memory fake honours the same port contract as the file repository. */
class InMemoryPostRepositoryTest extends PostRepositoryContract {

  @Override
  protected PostRepository newRepository() {
    return new InMemoryPostRepository();
  }
}
