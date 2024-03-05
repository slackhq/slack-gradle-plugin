package com.slack.sgp.intellij.circuitgen

import androidx.compose.material.Icon
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import java.util.Properties

class CreateCircuitFeatureFromTemplate: CreateFileFromTemplateAction("New Circuit Feature", "Create Circuit Feature", KotlinFileType.INSTANCE.icon) {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("Circuit Feature")
  }

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String {
    return ("Create My Class $newName")
  }

  override fun createFileFromTemplate(name: String?, template: FileTemplate?, dir: PsiDirectory?): PsiFile {
    if (dir != null) {
      val defaultProperties = FileTemplateManager.getInstance(dir.project).defaultProperties
      val properties = Properties(defaultProperties)
      val element = CreateFileFromTemplateDialog(/* project = */ dir.project)
    }
    return super.createFileFromTemplate(name, template, dir)
  }

}