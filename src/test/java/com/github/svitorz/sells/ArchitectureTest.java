package com.github.svitorz.sells;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.github.svitorz.sells", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.github.svitorz.sells";

    @ArchTest
    static final ArchRule domainDependsOnlyOnJava = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomain = classes()
            .that().resideInAPackage(ROOT + ".application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnUi = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".ui..", "javafx..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule uiDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(ROOT + ".ui..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".infrastructure..", "jakarta.persistence..", "org.hibernate..",
                    "org.springframework.data..", "org.springframework.jdbc..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule uiUsesOnlyEnumsAndDomainExceptionFromDomain = noClasses()
            .that().resideInAPackage(ROOT + ".ui..")
            .should().dependOnClassesThat(
                    resideInAPackage(ROOT + ".domain..")
                            .and(not(describe("are enums", JavaClass::isEnum)))
                            .and(not(name(ROOT + ".domain.shared.DomainException"))))
            .allowEmptyShould(true);
}
