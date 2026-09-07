package com.example.outbox;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.example.outbox";
    private static final String CONTROLLER_LAYER = ROOT_PACKAGE + ".controller..";
    private static final String DTO_PACKAGE = ROOT_PACKAGE + ".controller.dto..";
    private static final String SERVICE_LAYER = ROOT_PACKAGE + ".service..";
    private static final String REPOSITORY_LAYER = ROOT_PACKAGE + ".repository..";
    private static final String ENTITY_LAYER = ROOT_PACKAGE + ".entity..";
    private static final String WEB_PACKAGE = ROOT_PACKAGE + ".web..";
    private static final String HEALTH_PACKAGE = ROOT_PACKAGE + ".health..";
    private static final String CONFIG_PACKAGE = ROOT_PACKAGE + ".config..";

    private static final String SPRING_FRAMEWORK = "org.springframework..";
    private static final String JAKARTA_PERSISTENCE = "jakarta.persistence..";
    private static final String JAKARTA_SERVLET = "jakarta.servlet..";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT_PACKAGE);

    @Test
    void should_importOnlyProductionClasses_when_scanningRootPackage() {
        // then
        assertThat(PRODUCTION_CLASSES)
                .isNotEmpty()
                .allSatisfy(javaClass -> assertThat(javaClass.getName()).doesNotEndWith("Test"));
    }

    @Test
    void should_respectLayerOrdering_when_analysingWholeApplication() {
        // given
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy(CONTROLLER_LAYER)
                .layer("Service").definedBy(SERVICE_LAYER)
                .layer("Repository").definedBy(REPOSITORY_LAYER)
                .layer("Entity").definedBy(ENTITY_LAYER)
                .layer("Web").definedBy(WEB_PACKAGE)
                .layer("Health").definedBy(HEALTH_PACKAGE)
                .layer("Config").definedBy(CONFIG_PACKAGE)
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Health").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Health")
                .whereLayer("Entity").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Controller", "Config")
                .whereLayer("Config").mayOnlyBeAccessedByLayers("Service", "Web", "Health");

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notAccessRepositories_when_inControllerLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(CONTROLLER_LAYER)
                .should().dependOnClassesThat().resideInAPackage(REPOSITORY_LAYER);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notDependOnUpperLayers_when_inServiceLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(SERVICE_LAYER)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CONTROLLER_LAYER, WEB_PACKAGE, HEALTH_PACKAGE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notDependOnUpperLayers_when_inRepositoryLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(REPOSITORY_LAYER)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CONTROLLER_LAYER, SERVICE_LAYER, WEB_PACKAGE, HEALTH_PACKAGE, CONFIG_PACKAGE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notDependOnAnyOtherLayer_when_inEntityLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(ENTITY_LAYER)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CONTROLLER_LAYER, SERVICE_LAYER, REPOSITORY_LAYER,
                        WEB_PACKAGE, HEALTH_PACKAGE, CONFIG_PACKAGE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notDependOnSpring_when_inEntityLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(ENTITY_LAYER)
                .should().dependOnClassesThat().resideInAPackage(SPRING_FRAMEWORK);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beIndependentOfBusinessLayers_when_inCrossCuttingWebPackage() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(WEB_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CONTROLLER_LAYER, SERVICE_LAYER, REPOSITORY_LAYER,
                        ENTITY_LAYER, HEALTH_PACKAGE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beIndependentOfSiblingPackages_when_inCrossCuttingHealthPackage() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(HEALTH_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CONTROLLER_LAYER, SERVICE_LAYER, WEB_PACKAGE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beFreeOfCycles_when_slicingByTopLevelPackage() {
        // given
        ArchRule rule = slices()
                .matching(ROOT_PACKAGE + ".(*)..")
                .should().beFreeOfCycles()
                .ignoreDependency(resideInAPackage(CONFIG_PACKAGE), alwaysTrue());

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beInterfaces_when_inRepositoryLayer() {
        // given
        ArchRule rule = classes()
                .that().resideInAPackage(REPOSITORY_LAYER)
                .should().beInterfaces();

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_resideInRepositoryLayer_when_extendingSpringDataRepository() {
        // given
        ArchRule rule = classes()
                .that().areAssignableTo("org.springframework.data.repository.Repository")
                .should().resideInAPackage(REPOSITORY_LAYER);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_resideInControllerLayer_when_annotatedWithRestController() {
        // given
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(CONTROLLER_LAYER);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beDeclaredInServiceLayer_when_annotatedWithTransactional() {
        // given
        ArchRule rule = methods()
                .that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().resideInAPackage(SERVICE_LAYER);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_returnDtoInsteadOfResponseEntity_when_inControllerLayer() {
        // given
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage(CONTROLLER_LAYER)
                .should().haveRawReturnType(ResponseEntity.class);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_notReferenceJpaEntities_when_inControllerLayer() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage(CONTROLLER_LAYER)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beConfinedToControllerLayer_when_inDtoPackage() {
        // given
        ArchRule rule = classes()
                .that().resideInAPackage(DTO_PACKAGE)
                .should().onlyHaveDependentClassesThat().resideInAPackage(CONTROLLER_LAYER);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beRecords_when_inDtoPackage() {
        // given
        ArchRule rule = classes()
                .that().resideInAPackage(DTO_PACKAGE)
                .should().beRecords();

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beConfinedToEntityLayer_when_usingPersistenceAnnotations() {
        // given
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(ENTITY_LAYER)
                .should().dependOnClassesThat().resideInAPackage(JAKARTA_PERSISTENCE);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_beConfinedToWebPackage_when_usingServletTypes() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(CONTROLLER_LAYER, SERVICE_LAYER, REPOSITORY_LAYER,
                        ENTITY_LAYER, HEALTH_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(JAKARTA_SERVLET);

        // then
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_useConstructorInjection_when_wiringBeans() {
        // then
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_useSlf4jOnly_when_logging() {
        // then
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(PRODUCTION_CLASSES);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(PRODUCTION_CLASSES);
    }

    @Test
    void should_throwSpecificExceptions_when_failing() {
        // then
        NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(PRODUCTION_CLASSES);
    }
}
