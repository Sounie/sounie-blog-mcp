package nz.sounie.blogmcp.architecture;

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

  @ArchTest
  static final ArchRule bounded_contexts_are_free_of_cycles =
      slices().matching("nz.sounie.blogmcp.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

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
}
