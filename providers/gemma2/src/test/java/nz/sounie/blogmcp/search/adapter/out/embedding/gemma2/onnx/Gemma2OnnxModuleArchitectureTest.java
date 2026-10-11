package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** AC-SRCH-43: the provider depends inward on core's API, never on the composition root. */
@AnalyzeClasses(
    packages = "nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx",
    importOptions = ImportOption.DoNotIncludeTests.class)
class Gemma2OnnxModuleArchitectureTest {

  @ArchTest
  static final ArchRule provider_does_not_depend_on_app =
      noClasses().should().dependOnClassesThat().resideInAPackage("nz.sounie.blogmcp.app..");
}
