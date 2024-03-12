package com.slack.sgp.intellij.circuitgen

import androidx.compose.material.Icon
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import java.util.Properties

class CreateCircuitFeatureFromTemplate: CreateFileFromTemplateAction("New Circuit Feature", "Create Circuit Feature", KotlinFileType.INSTANCE.icon) {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("New Kotlin File/Class")
      .addKind("File", KotlinFileType.INSTANCE.icon, "Kotlin File")
      .addKind("Class", KotlinFileType.INSTANCE.icon, "Kotlin Class")
      .addKind("Interface", KotlinFileType.INSTANCE.icon, "Kotlin Interface")
      .addKind("Enum class", KotlinFileType.INSTANCE.icon, "Kotlin Enum")
      .addKind("Object", KotlinFileType.INSTANCE.icon, "Kotlin Object")
  }

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String {
    return ("Create My Class $newName")
  }

  override fun createFileFromTemplate(name: String?, template: FileTemplate?, dir: PsiDirectory?): PsiFile {
    if (dir != null) {
      val defaultProperties = FileTemplateManager.getInstance(dir.project).defaultProperties
      val properties = Properties(defaultProperties)
      val dialog = template?.let {
        CreateFromTemplateDialog(dir.project, dir,
          it,
          AttributesDefaults(name).withFixedName(true), properties)
      }
      dialog?.create()?.containingFile
    }
    return super.createFileFromTemplate(name, template, dir)
  }

}