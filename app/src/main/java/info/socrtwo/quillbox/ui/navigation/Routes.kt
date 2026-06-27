package info.socrtwo.quillbox.ui.navigation

/** Centralized navigation routes for the app's NavHost. */
object Routes {
    const val ACCOUNT_SETUP = "account_setup"
    const val FOLDERS = "folders"
    const val MESSAGES = "messages/{folderId}/{folderName}"
    const val DETAIL = "detail/{messageId}"
    const val COMPOSE = "compose"
    const val RULES = "rules"

    fun messages(folderId: Long, folderName: String) = "messages/$folderId/$folderName"
    fun detail(messageId: Long) = "detail/$messageId"
}
