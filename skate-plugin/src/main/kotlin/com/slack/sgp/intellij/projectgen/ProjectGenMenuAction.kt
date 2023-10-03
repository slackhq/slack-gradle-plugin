/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.intellij.projectgen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.SkatePluginSettings

class ProjectGenMenuAction
@JvmOverloads
constructor(private val terminalViewWrapper: TerminalViewWrapper? = null) : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject: Project = e.project ?: return
    val settings = currentProject.service<SkatePluginSettings>()
    val isProjectGenMenuActionEnabled = settings.isProjectGenMenuActionEnabled
    val projectGenRunCommand = settings.projectGenRunCommand
    if (!isProjectGenMenuActionEnabled) return
    executeProjectGenCommand(projectGenRunCommand, currentProject)
  }

  fun executeProjectGenCommand(command: String, project: Project) {
    val terminalCommand = TerminalCommand(command, project.basePath, PROJECT_GEN_TAB_NAME)
    if (terminalViewWrapper == null) {
      RealTerminalViewWrapper(project).executeCommand(terminalCommand)
    } else {
      terminalViewWrapper.executeCommand(terminalCommand)
    }
  }

  companion object {
    const val PROJECT_GEN_TAB_NAME: String = "ProjectGen"
  }
}
