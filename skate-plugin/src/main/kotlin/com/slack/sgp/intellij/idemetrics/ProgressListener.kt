package com.slack.sgp.intellij.idemetrics

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.progress.ProgressManagerListener
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorListener
import com.intellij.openapi.vfs.VirtualFileManagerListener

class ProgressListener : SaveAndSyncHandler() {
  override fun blockSaveOnFrameDeactivation() {
    TODO("Not yet implemented")
  }

  override fun blockSyncOnFrameActivation() {
    TODO("Not yet implemented")
  }

  override fun refreshOpenFiles() {
    TODO("Not yet implemented")
  }

  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    TODO("Not yet implemented")
  }

  override fun scheduleRefresh() {
    TODO("Not yet implemented")
  }

  override fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean) {
    TODO("Not yet implemented")
  }

  override fun unblockSaveOnFrameDeactivation() {
    TODO("Not yet implemented")
  }

  override fun unblockSyncOnFrameActivation() {
    TODO("Not yet implemented")
  }
}