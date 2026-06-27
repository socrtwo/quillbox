package info.socrtwo.quillbox.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import info.socrtwo.quillbox.desktop.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Quillbox") {
        App()
    }
}
