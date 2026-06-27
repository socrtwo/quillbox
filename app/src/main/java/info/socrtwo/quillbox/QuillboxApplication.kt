package info.socrtwo.quillbox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; enables Hilt dependency injection across the app. */
@HiltAndroidApp
class QuillboxApplication : Application()
