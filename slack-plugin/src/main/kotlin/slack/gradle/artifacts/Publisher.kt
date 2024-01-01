package slack.gradle.artifacts

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * Used for publishing custom artifacts from a subproject to an aggregating project (often the "root" project). Only
 * for inter-project publishing (e.g., _not_ for publishing to Artifactory). See also [Resolver].
 *
 * Represents a set of tightly coupled [Configuration]s:
 * * A "dependency scope" configuration ([Resolver.declarable]).
 * * A "resolvable" configuration ([Resolver.internal]).
 * * A "consumable" configuration ([external]).
 *
 * Dependencies are _declared_ on [Resolver.declarable] in the aggregating project. Custom artifacts (e.g., not jars),
 * generated by tasks, are published via [publish], which should be used on dependency (artifact-producing) projects.
 *
 * Gradle uses [attributes][Attr] to wire the consumer project's [Resolver.internal] (resolvable) configuration to the
 * producer project's [external] (consumable) configuration, which is itself configured via [publish].
 *
 * @see <a href="https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing">Variant-aware sharing of artifacts between projects</a>
 * @see <a href="https://dev.to/autonomousapps/configuration-roles-and-the-blogging-industrial-complex-21mn">Gradle configuration roles</a>
 */
internal class Publisher<T : Named>(
  project: Project,
  declarableName: String,
  attr: Attr<T>,
) {

  companion object {
    /** Convenience function for creating a [Publisher] for inter-project publishing of [SgpArtifacts]. */
    fun interProjectPublisher(
      project: Project,
      artifact: SgpArtifacts.Kind,
    ): Publisher<SgpArtifacts> {
      return Publisher(
        project,
        artifact.declarableName,
        Attr(SgpArtifacts.SGP_ARTIFACTS_ATTRIBUTE, artifact.artifactName)
      )
    }
  }

  // Following the naming pattern established by the Java Library plugin.
  // See https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph
  private val externalName = "${declarableName}Elements"

  /** The plugin will expose dependencies on this configuration, which extends from the declared dependencies. */
  private val external: NamedDomainObjectProvider<out Configuration> = run {
    if (project.configurations.findByName(externalName) != null) {
      project.configurations.named(externalName)
    } else {
      project.configurations.consumable(externalName) {
        // This attribute is identical to what is set on the internal/resolvable configuration
        attributes {
          attribute(
            attr.attribute,
            project.objects.named(attr.attribute.type, attr.attributeName)
          )
        }
      }
    }
  }

  /** Teach Gradle which thing produces the artifact associated with the external/consumable configuration. */
  fun publish(output: Provider<RegularFile>) {
    external.configure {
      outgoing.artifact(output)
    }
  }

  /** Teach Gradle which thing produces the artifact associated with the external/consumable configuration. */
  fun publishDirs(output: Provider<Directory>) {
    external.configure {
      outgoing.artifact(output)
    }
  }
}