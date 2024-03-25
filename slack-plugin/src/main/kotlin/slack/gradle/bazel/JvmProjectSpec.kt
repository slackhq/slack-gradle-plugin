/*
 * Copyright (C) 2024 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle.bazel

import com.grab.grazel.bazel.starlark.BazelDependency
import com.grab.grazel.bazel.starlark.asString
import com.grab.grazel.bazel.starlark.statements
import java.io.File
import java.util.SortedSet
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties

/** A spec for a plain kotlin jvm project. */
internal class JvmProjectSpec(builder: Builder) :
  CommonJvmProjectSpec by CommonJvmProjectSpec(builder) {
  override fun toString(): String {
    val kspTargets = kspProcessors.associateBy { it.name }
    val depsWithCodeGen = buildSet {
      addAll(kspTargets.keys.sorted().map { Dep.Target(it) })
      addAll(deps)
    }

    val compositeTestDeps =
      buildSet {
          add(Dep.Target("lib"))
          addAll(depsWithCodeGen)
          addAll(exportedDeps)
          addAll(testDeps)
          addAll(compilerPlugins)
        }
        .toSortedSet()

    /*
     load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_test")

     kt_jvm_library(
         name = "ditto_lib",
         srcs = glob(["src/main/**/*.kt"]),
         visibility = ["//visibility:public"],
         deps = [
             "@maven//:androidx_annotation_annotation",
         ],
     )

     kt_jvm_test(
         name = "ditto_test",
         srcs = glob(["src/test/**/*.kt"]),
         visibility = ["//visibility:private"],
         deps = [
             ":ditto_lib",
             "@maven//:androidx_annotation_annotation",
             "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_test",
             "@maven//:org_jetbrains_kotlin_kotlin_test",
             "@maven//:junit_junit",
             "@maven//:com_google_truth_truth",
         ],
     )
    */

    // Write statements in roughly the order of operations for readability
    return statements {
        for (processor in kspProcessors) {
          writeKspRule(processor)
        }

        slackKtLibrary(
          name = "lib",
          ruleSource = ruleSource,
          kotlinProjectType = KotlinProjectType.Jvm,
          srcsGlob = srcGlobs,
          visibility = Visibility.Public,
          deps =
            (depsWithCodeGen + compilerPlugins).sorted().map {
              BazelDependency.StringDependency(it.toString())
            },
          exportedDeps =
            exportedDeps.sorted().map { BazelDependency.StringDependency(it.toString()) },
        )

        // TODO only generate if there are actually matching test sources?
        slackKtTest(
          name = "test",
          ruleSource = ruleSource,
          associates = listOf(BazelDependency.StringDependency(":lib")),
          kotlinProjectType = KotlinProjectType.Jvm,
          srcsGlob = testSrcGlobs,
          deps = compositeTestDeps.map { BazelDependency.StringDependency(it.toString()) },
        )
      }
      .asString()
  }

  fun writeTo(path: Path, fs: FileSystem = FileSystem.SYSTEM) {
    path.parent?.let(fs::createDirectories)
    fs.write(path) { writeUtf8(this@JvmProjectSpec.toString()) }
  }

  class Builder(override val name: String) : CommonJvmProjectSpec.Builder<Builder> {
    override var ruleSource = "@rules_kotlin//kotlin:jvm.bzl"
    override val deps = mutableListOf<Dep>()
    override val exportedDeps = mutableListOf<Dep>()
    override val testDeps = mutableListOf<Dep>()
    override val srcGlobs = mutableListOf("src/main/**/*.kt", "src/main/**/*.java")
    override val testSrcGlobs = mutableListOf("src/test/**/*.kt", "src/test/**/*.java")
    override val compilerPlugins = mutableListOf<Dep>()
    override val kspProcessors = mutableListOf<KspProcessor>()

    fun build(): JvmProjectSpec = JvmProjectSpec(this)
  }
}

@UntrackedTask(because = "Generates a Bazel BUILD file for a Kotlin JVM project")
internal abstract class JvmProjectBazelTask : DefaultTask() {
  @get:Input abstract val targetName: Property<String>
  @get:Input abstract val ruleSource: Property<String>

  @get:Input abstract val projectDir: Property<File>

  @get:Input abstract val deps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val exportedDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val testDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val kspDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val kaptDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val compilerPlugins: SetProperty<Dep>
  @get:Input abstract val kspProcessors: SetProperty<KspProcessor>

  // Features
  @get:Optional @get:Input abstract val moshix: Property<Boolean>
  @get:Optional @get:Input abstract val redacted: Property<Boolean>
  @get:Optional @get:Input abstract val parcelize: Property<Boolean>
  @get:Optional @get:Input abstract val autoService: Property<Boolean>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  init {
    group = "bazel"
    description = "Generates a Bazel BUILD file for a Kotlin JVM project"
  }

  @TaskAction
  fun generate() {
    val deps = deps.mapDeps()
    val exportedDeps = exportedDeps.mapDeps()
    val testDeps = testDeps.mapDeps()

    // Only moshix and redacted are supported in JVM projects
    val compilerPlugins = compilerPlugins.get().toMutableList()

    val kspProcessors = kspProcessors.get().toMutableList()

    if (moshix.getOrElse(false)) {
      // TODO we technically could choose IR or KSP for this, but for now assume IR
      compilerPlugins.add(CompilerPluginDeps.moshix)
      kspProcessors.add(KspProcessors.moshiProguardRuleGen)
    }
    if (redacted.getOrElse(false)) {
      compilerPlugins.add(CompilerPluginDeps.redacted)
    }
    if (parcelize.getOrElse(false)) {
      compilerPlugins.add(CompilerPluginDeps.parcelize)
    }
    if (autoService.getOrElse(false)) {
      kspProcessors.add(KspProcessors.autoService)
    }

    // TODO make this pluggable and single-pass
    val mappedKspDeps = kspDeps.mapDeps()
    when {
      mappedKspDeps.any { "guinness" in it.toString() } -> {
        logger.lifecycle("[KSP] Adding guinness compiler")
        kspProcessors += KspProcessors.guinness
      }
      mappedKspDeps.any { "feature-flag/compiler" in it.toString() } -> {
        logger.lifecycle("[KSP] Adding feature flag compiler")
        kspProcessors += KspProcessors.featureFlag
      }
    }
    val allKspDeps = mappedKspDeps.map { it.toString() }

    // TODO kapt

    JvmProjectSpec.Builder(targetName.get())
      .apply {
        this@JvmProjectBazelTask.ruleSource.orNull?.let(::ruleSource)
        deps.forEach { addDep(it) }
        exportedDeps.forEach { addExportedDep(it) }
        testDeps.forEach { addTestDep(it) }
        compilerPlugins.forEach { addCompilerPlugin(it) }
        kspProcessors.forEach { addKspProcessor(it.withAddedDeps(allKspDeps)) }
      }
      .build()
      .writeTo(outputFile.asFile.get().toOkioPath())
  }

  private fun SetProperty<ComponentArtifactIdentifier>.mapDeps(): SortedSet<Dep> {
    return map { result ->
        result.asSequence().mapNotNull { component ->
          when (component) {
            is ModuleComponentArtifactIdentifier -> {
              val componentId = component.componentIdentifier
              val identifier = "${componentId.group}:${componentId.module}"
              Dep.Remote.fromMavenIdentifier(identifier)
            }
            is PublishArtifactLocalArtifactMetadata -> {
              val projectIdentifier = component.componentIdentifier
              check(projectIdentifier is ProjectComponentIdentifier)
              // Map to "path/to/local/dependency1" format
              Dep.Local(
                projectIdentifier.projectPath.removePrefix(":").replace(":", "/"),
                target = "lib",
              )
            }
            else -> {
              System.err.println("Unknown component type: $component (${component.javaClass})")
              null
            }
          }
        }
      }
      .get()
      .toSortedSet()
  }

  protected fun resolvedDependenciesFrom(
    provider: NamedDomainObjectProvider<ResolvableConfiguration>
  ): Provider<List<ComponentArtifactIdentifier>> {
    return provider.flatMap { configuration ->
      configuration.incoming.artifacts.resolvedArtifacts.map { it.map { it.id } }
    }
  }

  companion object {
    fun register(
      project: Project,
      slackProperties: SlackProperties,
      depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      kspConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      kaptConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      slackExtension: SlackExtension,
    ) {
      project.tasks
        .register("generateBazel", JvmProjectBazelTask::class.java, slackProperties)
        .configure {
          targetName.set(project.name)
          ruleSource.set(slackProperties.bazelRuleSource)
          projectDir.set(project.layout.projectDirectory.asFile)
          deps.set(resolvedDependenciesFrom(depsConfiguration))
          exportedDeps.set(resolvedDependenciesFrom(exportedDepsConfiguration))
          testDeps.set(resolvedDependenciesFrom(testConfiguration))
          kspConfiguration?.let { kspDeps.set(resolvedDependenciesFrom(it)) }
          kaptConfiguration?.let { kaptDeps.set(resolvedDependenciesFrom(it)) }
          outputFile.set(project.layout.projectDirectory.file("BUILD.bazel"))
          moshix.set(slackExtension.featuresHandler.moshiHandler.moshiCodegen)
          redacted.set(slackExtension.featuresHandler.redacted)
          parcelize.set(project.pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.parcelize"))
          autoService.set(slackExtension.featuresHandler.autoService)
        }
    }
  }
}
