package nz.sounie.blogmcp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

  // ADR 0007: the file storage helpers use JDK types only (stricter than the `shared` rule: not
  // even
  // the other `shared` packages).
  @ArchTest
  static final ArchRule shared_storage_uses_jdk_types_only =
      classes()
          .that()
          .resideInAPackage("nz.sounie.blogmcp.shared.storage..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("nz.sounie.blogmcp.shared.storage..", "java..")
          .allowEmptyShould(true);

  // ADR 0007: file storage is infrastructure for outbound adapters (and the composition root);
  // the domain and application layers never touch files.
  @ArchTest
  static final ArchRule file_storage_stays_in_adapters =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("nz.sounie.blogmcp.shared.storage..")
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

  // A public member of an accessible class must not mention a type that callers outside its
  // package cannot name: parameters, return types, fields (record components via their
  // accessors), declared exceptions and type arguments such as List<IndexedChunk>. Two
  // practical extensions:
  // - unchecked exceptions the class throws (constructs) count as exposed, because callers must
  //   catch them by type (e.g. InvalidSearchQuery) even though they are never declared;
  // - a hidden type that the API exposes is itself checked as if it were public, so types it
  //   would expose next (e.g. ChunkHit via IndexedChunk.hitFor) are reported too.
  // The "constructs a Throwable" check is a heuristic. It is deliberately strict: an exception
  // constructed and caught entirely inside a public class is still flagged. It is also blind to an
  // exception constructed in a package-private helper and propagated. On a false positive, make the
  // exception public or move its construction; never weaken this rule.
  @ArchTest
  static final ArchRule public_api_exposes_only_public_types =
      classes()
          .that()
          .resideInAPackage("nz.sounie.blogmcp..")
          .and()
          .arePublic()
          .should(exposeOnlyPublicTypes())
          .allowEmptyShould(true);

  private static ArchCondition<JavaClass> exposeOnlyPublicTypes() {
    return new ArchCondition<>("expose only public types in their public API") {
      @Override
      public void check(JavaClass owner, ConditionEvents events) {
        if (isAccessible(owner)) {
          checkApi(owner, "", events, new HashSet<>(Set.of(owner)));
        }
      }
    };
  }

  private static void checkApi(
      JavaClass type, String via, ConditionEvents events, Set<JavaClass> visited) {
    type.getMembers().stream()
        .filter(member -> member.getModifiers().contains(JavaModifier.PUBLIC))
        .filter(member -> !member.getModifiers().contains(JavaModifier.SYNTHETIC))
        .forEach(
            member ->
                exposedTypes(member)
                    .filter(exposed -> !isAccessible(exposed) && !exposed.equals(type))
                    .distinct()
                    .forEach(
                        hidden -> {
                          events.add(
                              SimpleConditionEvent.violated(
                                  member,
                                  via
                                      + member.getFullName()
                                      + " exposes non-public type "
                                      + hidden.getName()));
                          if (visited.add(hidden)) {
                            checkApi(
                                hidden,
                                via + "(via " + hidden.getSimpleName() + ") ",
                                events,
                                visited);
                          }
                        }));
    thrownExceptions(type)
        .filter(exception -> !isAccessible(exception))
        .distinct()
        .forEach(
            hidden ->
                events.add(
                    SimpleConditionEvent.violated(
                        type,
                        via
                            + type.getName()
                            + " throws non-public exception "
                            + hidden.getName())));
  }

  private static Stream<JavaClass> exposedTypes(JavaMember member) {
    Stream<JavaType> signature =
        switch (member) {
          case JavaField field -> Stream.of(field.getType());
          case JavaCodeUnit unit ->
              Stream.concat(
                  Stream.of(unit.getReturnType()),
                  unit.getParameters().stream().map(p -> p.getType()));
          default -> Stream.empty();
        };
    Stream<JavaClass> declared =
        member instanceof JavaCodeUnit unit
            ? unit.getThrowsClause().getTypes().stream()
            : Stream.empty();
    return Stream.concat(signature.flatMap(t -> t.getAllInvolvedRawTypes().stream()), declared)
        .map(LayeringTest::baseComponent);
  }

  /** Exceptions the class constructs, in any of its code units (they may escape its API). */
  private static Stream<JavaClass> thrownExceptions(JavaClass type) {
    return type.getCodeUnits().stream()
        .flatMap(unit -> unit.getConstructorCallsFromSelf().stream())
        .map(call -> call.getTargetOwner())
        .filter(target -> target.isAssignableTo(Throwable.class));
  }

  private static JavaClass baseComponent(JavaClass type) {
    return type.isArray() ? type.getBaseComponentType() : type;
  }

  /** Public, and nested only inside public classes. */
  private static boolean isAccessible(JavaClass type) {
    Optional<JavaClass> enclosing = type.getEnclosingClass();
    return type.getModifiers().contains(JavaModifier.PUBLIC)
        && enclosing.map(LayeringTest::isAccessible).orElse(true);
  }
}
