package dev.aga.gradle.versioncatalogs.mock

import java.util.concurrent.TimeUnit
import org.gradle.api.Action
import org.gradle.api.artifacts.CapabilitiesResolution
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.provider.Property
import org.gradle.internal.ImmutableActionSet

internal class ResolutionStrategy : ResolutionStrategyInternal {
    override fun failOnVersionConflict(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun failOnDynamicVersions(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun failOnChangingVersions(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun failOnNonReproducibleResolution(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun preferProjectModules() {
        TODO("Not yet implemented")
    }

    override fun activateDependencyLocking(): org.gradle.api.artifacts.ResolutionStrategy {
        return this
    }

    override fun deactivateDependencyLocking(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun disableDependencyVerification(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun enableDependencyVerification(): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun force(
        vararg moduleVersionSelectorNotations: Any?
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun setForcedModules(
        vararg moduleVersionSelectorNotations: Any?
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun getForcedModules(): MutableSet<ModuleVersionSelector> {
        TODO("Not yet implemented")
    }

    override fun eachDependency(
        rule: Action<in DependencyResolveDetails>
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun cacheDynamicVersionsFor(value: Int, units: String) {
        TODO("Not yet implemented")
    }

    override fun cacheDynamicVersionsFor(value: Int, units: TimeUnit) {
        TODO("Not yet implemented")
    }

    override fun cacheChangingModulesFor(value: Int, units: String) {
        TODO("Not yet implemented")
    }

    override fun cacheChangingModulesFor(value: Int, units: TimeUnit) {
        TODO("Not yet implemented")
    }

    override fun getComponentSelection(): ComponentSelectionRulesInternal {
        TODO("Not yet implemented")
    }

    override fun componentSelection(
        action: Action<in ComponentSelectionRules>
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun getDependencySubstitution(): DependencySubstitutionsInternal {
        TODO("Not yet implemented")
    }

    override fun dependencySubstitution(
        action: Action<in DependencySubstitutions>
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun getUseGlobalDependencySubstitutionRules(): Property<Boolean> {
        TODO("Not yet implemented")
    }

    override fun sortArtifacts(sortOrder: org.gradle.api.artifacts.ResolutionStrategy.SortOrder) {
        TODO("Not yet implemented")
    }

    override fun capabilitiesResolution(
        action: Action<in CapabilitiesResolution>
    ): org.gradle.api.artifacts.ResolutionStrategy {
        TODO("Not yet implemented")
    }

    override fun getCapabilitiesResolution(): CapabilitiesResolution {
        TODO("Not yet implemented")
    }

    override fun maybeDiscardStateRequiredForGraphResolution() {
        TODO("Not yet implemented")
    }

    override fun setKeepStateRequiredForGraphResolution(
        keepStateRequiredForGraphResolution: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun getCachePolicy(): CachePolicy {
        TODO("Not yet implemented")
    }

    override fun getConflictResolution(): ConflictResolution {
        TODO("Not yet implemented")
    }

    override fun getDependencySubstitutionRule():
        ImmutableActionSet<DependencySubstitutionInternal> {
        TODO("Not yet implemented")
    }

    override fun assumeFluidDependencies() {
        TODO("Not yet implemented")
    }

    override fun resolveGraphToDetermineTaskDependencies(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSortOrder(): org.gradle.api.artifacts.ResolutionStrategy.SortOrder {
        TODO("Not yet implemented")
    }

    override fun copy(): ResolutionStrategyInternal {
        TODO("Not yet implemented")
    }

    override fun setMutationValidator(action: MutationValidator) {
        TODO("Not yet implemented")
    }

    override fun getDependencyLockingProvider(): DependencyLockingProvider {
        TODO("Not yet implemented")
    }

    override fun isDependencyLockingEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun confirmUnlockedConfigurationResolved(configurationName: String) {
        TODO("Not yet implemented")
    }

    override fun getCapabilitiesResolutionRules(): CapabilitiesResolutionInternal {
        TODO("Not yet implemented")
    }

    override fun isFailingOnDynamicVersions(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFailingOnChangingVersions(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isDependencyVerificationEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setReturnAllVariants(returnAllVariants: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getReturnAllVariants(): Boolean {
        TODO("Not yet implemented")
    }
}
