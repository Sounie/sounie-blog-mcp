package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundledModelFilesTest {

  private static final String FIXTURE = "/bundled-model-fixture";

  @Test
  void copies_each_named_resource_into_one_directory_under_its_own_name() {
    Path directory =
        BundledModelFiles.extract(
            BundledModelFilesTest.class, FIXTURE, List.of("graph.onnx", "graph.onnx_data"));

    assertThat(directory.resolve("graph.onnx")).hasContent("graph");
    assertThat(directory.resolve("graph.onnx_data")).hasContent("weights");
  }

  @Test
  void each_extraction_gets_its_own_directory() {
    Path first = BundledModelFiles.extract(BundledModelFilesTest.class, FIXTURE, List.of());
    Path second = BundledModelFiles.extract(BundledModelFilesTest.class, FIXTURE, List.of());

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void a_missing_resource_fails_naming_it() {
    assertThatThrownBy(
            () ->
                BundledModelFiles.extract(
                    BundledModelFilesTest.class, FIXTURE, List.of("absent.onnx")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(FIXTURE + "/absent.onnx");
  }
}
