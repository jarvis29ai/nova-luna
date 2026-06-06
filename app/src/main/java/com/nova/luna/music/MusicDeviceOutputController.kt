package com.nova.luna.music

/**
 * Controller for managing audio output devices.
 */
class MusicDeviceOutputController {

    fun setOutputDevice(preference: MusicDeviceOutputPreference) {
        // In a real app, this might use AudioDeviceInfo or BluetoothManager
    }

    fun getCurrentOutput(): MusicDeviceOutputPreference {
        return MusicDeviceOutputPreference.PHONE_SPEAKER
    }
}
