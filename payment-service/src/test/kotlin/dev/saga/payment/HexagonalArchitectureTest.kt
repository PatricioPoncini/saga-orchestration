package dev.saga.payment

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(packages = ["dev.saga.payment"], importOptions = [ImportOption.DoNotIncludeTests::class])
class HexagonalArchitectureTest {
    @ArchTest
    @JvmField
    val domainIsPure: ArchRule = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "..application..", "..adapter..", "dev.saga.messaging..")

    @ArchTest
    @JvmField
    val applicationDoesNotKnowAdapters: ArchRule = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "..adapter..", "dev.saga.messaging..")
}
