package com.jhani.measurear.ar

/**
 * Represents the lifecycle and readiness state of the ARCore Session.
 */
sealed class ARSessionState {
    object Idle : ARSessionState()
    object CheckingAvailability : ARSessionState()
    object PermissionRequired : ARSessionState()
    object InstallingArcore : ARSessionState()
    object SessionReady : ARSessionState()
    data class UnsupportedDevice(val message: String) : ARSessionState()
    data class Error(val message: String) : ARSessionState()
}
