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
package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.slack.sgp.intellij.fakes.FakeTerminalViewWrapper
import com.slack.sgp.intellij.projectgen.ProjectGenMenuAction
import com.slack.sgp.intellij.projectgen.ProjectGenMenuAction.Companion.PROJECT_GEN_TAB_NAME

class ProjectGenMenuActionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()

    // Reset relevant settings.
    val settings = project.service<SkatePluginSettings>()
    settings.isProjectGenMenuActionEnabled = true
    settings.projectGenRunCommand = "echo Hello World"
  }

  fun testCorrectArgumentsPassedIntoTerminalView() {
    val action = ProjectGenMenuAction()
    action.terminalViewWrapper = FakeTerminalViewWrapper()

    // Perform action
    myFixture.testAction(action)

    // Verify right arguments are passed into the terminal
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).commandRan)
      .isEqualTo("echo Hello World")
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).projectPath)
      .isEqualTo(project.basePath)
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).tabName)
      .isEqualTo(PROJECT_GEN_TAB_NAME)
  }

  fun testTerminalViewNotRunningWhenActionDisabled() {
    val action = ProjectGenMenuAction()
    action.terminalViewWrapper = FakeTerminalViewWrapper()
    project.service<SkatePluginSettings>().isProjectGenMenuActionEnabled = false

    // Perform action
    myFixture.testAction(action)

    // Verify action didn't run any terminal command
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).commandRan).isNull()
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).projectPath).isNull()
    assertThat((action.terminalViewWrapper as FakeTerminalViewWrapper).tabName).isNull()
  }
}
