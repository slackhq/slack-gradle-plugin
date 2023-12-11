package slack.gradle.avoidance

import okio.FileSystem
import okio.Path
import slack.gradle.util.prepareForGradleOutput

public interface SkippyOutput {
  /** The tool-specific directory. */
  public val subDir: Path

  /** The output list of affected projects. */
  public val affectedProjectsFile: Path

  /** The output list of affected androidTest projects. */
  public val affectedAndroidTestProjectsFile: Path

  /** An output .focus file that could be used with the Focus plugin. */
  public val outputFocusFile: Path
}

public class SimpleSkippyOutput(public override val subDir: Path) : SkippyOutput {
  public override val affectedProjectsFile: Path = subDir.resolve("affected_projects.txt")
  public override val affectedAndroidTestProjectsFile: Path =
    subDir.resolve("affected_android_test_projects.txt")
  public override val outputFocusFile: Path = subDir.resolve("focus.settings.gradle")
}

public class WritableSkippyOutput(tool: String, outputDir: Path, fs: FileSystem) : SkippyOutput {
  internal val delegate = SimpleSkippyOutput(outputDir.resolve(tool))

  // Eagerly init the subdir and clear it if exists
  public override val subDir: Path =
    delegate.subDir.apply {
      if (fs.exists(this)) {
        fs.deleteRecursively(this)
      }
    }

  public override val affectedProjectsFile: Path by lazy {
    delegate.affectedProjectsFile.prepareForGradleOutput(fs)
  }

  public override val affectedAndroidTestProjectsFile: Path by lazy {
    delegate.affectedAndroidTestProjectsFile.prepareForGradleOutput(fs)
  }

  public override val outputFocusFile: Path by lazy {
    delegate.outputFocusFile.prepareForGradleOutput(fs)
  }
}
