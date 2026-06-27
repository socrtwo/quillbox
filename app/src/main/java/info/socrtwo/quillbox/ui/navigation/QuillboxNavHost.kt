package info.socrtwo.quillbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import info.socrtwo.quillbox.ui.account.AccountSetupScreen
import info.socrtwo.quillbox.ui.compose.ComposeScreen
import info.socrtwo.quillbox.ui.folders.FolderListScreen
import info.socrtwo.quillbox.ui.messages.MessageDetailScreen
import info.socrtwo.quillbox.ui.messages.MessageListScreen
import info.socrtwo.quillbox.ui.rules.RulesScreen
import info.socrtwo.quillbox.ui.root.RootViewModel

@Composable
fun QuillboxNavHost(rootViewModel: RootViewModel) {
    val navController = rememberNavController()
    val hasAccount by rootViewModel.hasAccount.collectAsStateWithLifecycle()

    // Wait until we know whether an account exists before choosing the start screen.
    val start = when (hasAccount) {
        null -> return // still loading
        true -> Routes.FOLDERS
        false -> Routes.ACCOUNT_SETUP
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ACCOUNT_SETUP) {
            AccountSetupScreen(
                onSaved = {
                    navController.navigate(Routes.FOLDERS) {
                        popUpTo(Routes.ACCOUNT_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.FOLDERS) {
            FolderListScreen(
                onOpenFolder = { id, name -> navController.navigate(Routes.messages(id, name)) },
                onCompose = { navController.navigate(Routes.COMPOSE) },
                onManageRules = { navController.navigate(Routes.RULES) }
            )
        }

        composable(
            route = Routes.MESSAGES,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) {
            MessageListScreen(
                onOpenMessage = { messageId -> navController.navigate(Routes.detail(messageId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("messageId") { type = NavType.LongType })
        ) {
            MessageDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.COMPOSE) {
            ComposeScreen(onSent = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }

        composable(Routes.RULES) {
            RulesScreen(onBack = { navController.popBackStack() })
        }
    }
}
