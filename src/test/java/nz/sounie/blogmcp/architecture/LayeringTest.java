package nz.sounie.blogmcp.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "nz.sounie.blogmcp",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest {

  @ArchTest
  static final ArchRule domain_depends_only_on_itself_and_the_jdk =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..")
          .orShould()
          .dependOnClassesThat()
          .resideOutsideOfPackages("nz.sounie.blogmcp..", "java..", "javax.annotation..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_does_not_depend_on_adapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule inbound_and_outbound_adapters_are_independent =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..")
          .allowEmptyShould(true);

  // `app` is the composition root: it depends on every context by design, and nothing depends on
  // it (see the rule below), so its outgoing dependencies cannot close a cycle between contexts.
  @ArchTest
  static final ArchRule bounded_contexts_are_free_of_cycles =
      slices()
          .matching("nz.sounie.blogmcp.(*)..")
          .should()
          .beFreeOfCycles()
          .ignoreDependency(resideInAPackage("nz.sounie.blogmcp.app.."), alwaysTrue())
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule nothing_depends_on_the_composition_root =
      noClasses()
          .that()
          .resideOutsideOfPackage("nz.sounie.blogmcp.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("nz.sounie.blogmcp.app..")
          .allowEmptyShould(true);

  // Within each context, the domain sub-packages (e.g. catalog: site <- post <- sync) are acyclic.
  @ArchTest
  static final ArchRule domain_sub_packages_are_free_of_cycles =
      slices()
          .matching("nz.sounie.blogmcp.(*).domain.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true);

  // ADR 0003: the published language in `shared` uses JDK types only and depends on no context.
  @ArchTest
  static final ArchRule shared_depends_on_no_bounded_context =
      classes()
          .that()
          .resideInAPackage("nz.sounie.blogmcp.shared..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("nz.sounie.blogmcp.shared..", "java..")
          .allowEmptyShould(true);

  // ADR 0003: search learns about posts only through the integration events in `shared`.
  @ArchTest
  static final ArchRule search_does_not_depend_on_catalog =
      noClasses()
          .that()
          .resideInAPackage("nz.sounie.blogmcp.search..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("nz.sounie.blogmcp.catalog..")
          .allowEmptyShould(true);

  // ADR 0003: our code uses Jackson 3 (tools.jackson) only; Jackson 2 may arrive transitively.
  @ArchTest
  static final ArchRule no_jackson_2 =
      noClasses()
          .that()
          .resideInAPackage("nz.sounie.blogmcp..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.fasterxml.jackson.databind..", "com.fasterxml.jackson.core..")
          .allowEmptyShould(true);

  // ADR 0003: HTTP, JSON and HTML libraries are adapter concerns. The domain rule above already
  // excludes third-party libraries; this also keeps them (and the JDK HTTP client) out of the
  // application layer and the domain.
  @ArchTest
  static final ArchRule http_json_and_html_handling_stay_in_adapters =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "tools.jackson..", "com.fasterxml.jackson..", "org.jsoup..", "java.net.http..")
          .allowEmptyShould(true);

  // ADR 0005: the local model libraries are wrapped by search's outbound adapters only.
  @ArchTest
  static final ArchRule model_libraries_only_in_search_outbound_adapters =
      noClasses()
          .that()
          .resideOutsideOfPackage("nz.sounie.blogmcp.search.adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..", "ai.onnxruntime..", "ai.djl..")
          .allowEmptyShould(true);

  // ADR 0005: OnnxEmbedder is the only class that touches LangChain4j (and ONNX Runtime).
  @ArchTest
  static final ArchRule only_onnx_embedder_uses_langchain4j =
      noClasses()
          .that()
          .resideInAPackage("nz.sounie.blogmcp..")
          .and()
          .haveNameNotMatching(
              "nz\\.sounie\\.blogmcp\\.search\\.adapter\\.out\\.OnnxEmbedder(\\$.*)?")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..", "ai.onnxruntime..")
          .allowEmptyShould(true);

  // ADR 0005: BgeTokenCounter is the only class that touches DJL, and only its tokenizers.
  @ArchTest
  static final ArchRule only_bge_token_counter_uses_djl =
      noClasses()
          .that()
          .resideInAPackage("nz.sounie.blogmcp..")
          .and()
          .haveNameNotMatching(
              "nz\\.sounie\\.blogmcp\\.search\\.adapter\\.out\\.BgeTokenCounter(\\$.*)?")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("ai.djl..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule bge_token_counter_uses_only_djl_tokenizers =
      noClasses()
          .that()
          .haveNameMatching(
              "nz\\.sounie\\.blogmcp\\.search\\.adapter\\.out\\.BgeTokenCounter(\\$.*)?")
          .should()
          .dependOnClassesThat(
              resideInAPackage("ai.djl..")
                  .and(resideOutsideOfPackage("ai.djl.huggingface.tokenizers..")))
          .allowEmptyShould(true);

  // ADR 0005: the shared query contract uses JDK types only.
  @ArchTest
  static final ArchRule shared_query_uses_jdk_types_only =
      classes()
          .that()
          .resideInAPackage("nz.sounie.blogmcp.shared.query..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("nz.sounie.blogmcp.shared.query..", "java..")
          .allowEmptyShould(true);

  // search.md section 6: search translates the published language at its adapters (anti-corruption
  // layer), so neither its domain nor its application layer sees `shared` types.
  @ArchTest
  static final ArchRule search_core_does_not_see_the_published_language =
      noClasses()
          .that()
          .resideInAnyPackage(
              "nz.sounie.blogmcp.search.domain..", "nz.sounie.blogmcp.search.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("nz.sounie.blogmcp.shared..")
          .allowEmptyShould(true);
}
