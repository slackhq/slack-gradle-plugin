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

import com.android.build.gradle.internal.publishing.AndroidArtifacts
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
import slack.gradle.SlackExtension
import slack.gradle.register

/** A spec for a plain kotlin jvm project. */
internal class JvmProjectSpec(builder: Builder) {
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String = builder.name
  /** The source for rules to import. */
  val ruleSource: String = builder.ruleSource
  // Deps
  val deps: List<Dep> = builder.deps.toList()
  val exportedDeps: List<Dep> = builder.exportedDeps.toList()
  val testDeps: List<Dep> = (builder.testDeps + Dep.Target("${name}_lib")).toList()
  // Source globs
  val srcGlobs: List<String> = builder.srcGlobs.toList()
  val testSrcGlobs: List<String> = builder.testSrcGlobs.toList()
  val compilerPlugins = builder.compilerPlugins.toList()

  override fun toString(): String {
    val compositeTestDeps = (deps + exportedDeps + testDeps + compilerPlugins).toSortedSet()

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

    return statements {
        slackKtLibrary(
          name = "${name}_lib",
          kotlinProjectType = KotlinProjectType.Jvm,
          srcsGlob = srcGlobs,
          visibility = Visibility.Public,
          deps =
            (deps + compilerPlugins).sorted().map {
              BazelDependency.StringDependency(it.toString())
            },
          exportedDeps =
            exportedDeps.sorted().map { BazelDependency.StringDependency(it.toString()) },
        )

        slackKtTest(
          name = "${name}_test",
          kotlinProjectType = KotlinProjectType.Jvm,
          srcsGlob = testSrcGlobs,
          visibility = Visibility.Private,
          deps = compositeTestDeps.map { BazelDependency.StringDependency(it.toString()) },
        )
      }
      .asString()
  }

  fun writeTo(path: Path, fs: FileSystem = FileSystem.SYSTEM) {
    path.parent?.let(fs::createDirectories)
    fs.write(path) { writeUtf8(this@JvmProjectSpec.toString()) }
  }

  class Builder(val name: String) {
    var ruleSource = "@rules_kotlin//kotlin:jvm.bzl"
    val deps = mutableListOf<Dep>()
    val exportedDeps = mutableListOf<Dep>()
    val testDeps = mutableListOf<Dep>()
    val srcGlobs = mutableListOf("src/main/**/*.kt", "src/main/**/*.java")
    val testSrcGlobs = mutableListOf("src/test/**/*.kt", "src/test/**/*.java")
    val compilerPlugins = mutableListOf<Dep>()

    fun ruleSource(source: String) = apply { ruleSource = source }

    fun addDep(dep: Dep) = apply { deps.add(dep) }

    fun addExportedDep(dep: Dep) = apply { exportedDeps.add(dep) }

    fun addTestDep(dep: Dep) = apply { testDeps.add(dep) }

    fun addSrcGlob(glob: String) = apply { srcGlobs.add(glob) }

    fun addTestSrcGlob(glob: String) = apply { testSrcGlobs.add(glob) }

    fun addCompilerPlugin(plugin: Dep) = apply { compilerPlugins.add(plugin) }

    fun build(): JvmProjectSpec = JvmProjectSpec(this)
  }
}

@UntrackedTask(because = "Generates a Bazel BUILD file for a Kotlin JVM project")
internal abstract class JvmProjectBazelTask : DefaultTask() {
  @get:Input abstract val targetName: Property<String>
  @get:Optional @get:Input abstract val ruleSource: Property<String>

  @get:Input abstract val projectDir: Property<File>

  @get:Input abstract val deps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val exportedDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val testDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input abstract val compilerPlugins: SetProperty<Dep>

  // Compiler plugins
  @get:Optional @get:Input abstract val moshix: Property<Boolean>
  @get:Optional @get:Input abstract val redacted: Property<Boolean>
  @get:Optional @get:Input abstract val parcelize: Property<Boolean>

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
    val compilerPlugins = compilerPlugins.get()
    buildSet {
      // TODO we technically could choose IR or KSP for this, but for now assume IR
      if (moshix.getOrElse(false)) {
        add(CompilerPluginDeps.moshix)
      }
      if (redacted.getOrElse(false)) {
        add(CompilerPluginDeps.redacted)
      }
      if (parcelize.getOrElse(false)) {
        add(CompilerPluginDeps.parcelize)
      }
    }

    JvmProjectSpec.Builder(targetName.get())
      .apply {
        this@JvmProjectBazelTask.ruleSource.orNull?.let(::ruleSource)
        deps.forEach { addDep(it) }
        exportedDeps.forEach { addExportedDep(it) }
        testDeps.forEach { addTestDep(it) }
        compilerPlugins.forEach { addCompilerPlugin(it) }
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
            is ProjectComponentIdentifier -> {
              // Map to "path/to/local/dependency1" format
              Dep.Local(component.projectPath.removePrefix(":").replace(":", "/"))
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
      configuration.incoming
        .artifactView {
          attributes {
            attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.AAR_OR_JAR.type)
          }
          lenient(true)
        }
        .artifacts
        .resolvedArtifacts
        .map { it.map { it.id } }
    }
  }

  companion object {
    fun register(
      project: Project,
      depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      slackExtension: SlackExtension,
    ) {
      project.tasks.register<JvmProjectBazelTask>("generateBazel") {
        targetName.set(project.name)
        projectDir.set(project.layout.projectDirectory.asFile)
        deps.set(resolvedDependenciesFrom(depsConfiguration))
        exportedDeps.set(resolvedDependenciesFrom(exportedDepsConfiguration))
        testDeps.set(resolvedDependenciesFrom(testConfiguration))
        outputFile.set(project.layout.projectDirectory.file("BUILD.bazel"))
        moshix.set(slackExtension.featuresHandler.moshiHandler.moshiCodegen)
        redacted.set(slackExtension.featuresHandler.redacted)
      }
    }
  }
}