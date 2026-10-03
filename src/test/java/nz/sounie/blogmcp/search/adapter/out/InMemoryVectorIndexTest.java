package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import org.junit.jupiter.api.Test;

class InMemoryVectorIndexTest extends VectorIndexContract {

  @Override
  protected VectorIndex newIndex() {
    return new InMemoryVectorIndex();
  }

  @Test
  void shares_the_saved_immutable_instance() {
    index.save(first);

    assertThat(index.find(post.id())).containsSame(first);
    assertThat(index.all()).containsExactly(first);
  }
}
