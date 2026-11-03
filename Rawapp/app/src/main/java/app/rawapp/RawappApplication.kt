package app.rawapp

import android.app.Application
import app.rawapp.develop.RawDeveloper
import app.rawapp.storage.SettingsStore

/**
 * Process-wide singletons: one GL developer shared by the camera (auto-export)
 * and the gallery, so only one EGL context and one set of GPU buffers exist.
 */
class RawappApplication : Application() {
    val developer: RawDeveloper by lazy { RawDeveloper(assets) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
