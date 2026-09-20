package com.hermes.client.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hermes.client.feature.chat.ChatScreen
import com.hermes.client.feature.conversations.ConversationsScreen
import com.hermes.client.feature.models.ModelSelectorScreen
import com.hermes.client.feature.server.ServerConnectionScreen
import com.hermes.client.feature.settings.ProvidersScreen
import com.hermes.client.feature.settings.SettingsScreen
import com.hermes.client.feature.tasks.TasksScreen

object HermesRoutes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat/{conversationId}"
    const val CONVERSATIONS = "conversations"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val SERVER_CONNECTION = "server_connection"
    const val TASKS = "tasks"
    const val PROVIDERS = "providers"
}

@Composable
fun HermesNavHost(
    navController: NavHostController,
    startDestination: String = HermesRoutes.CHAT
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Chat screen (new conversation)
        composable(HermesRoutes.CHAT) {
            ChatScreen(
                conversationId = null,
                onNavigateToConversations = {
                    navController.navigate(HermesRoutes.CONVERSATIONS)
                },
                onNavigateToSettings = {
                    navController.navigate(HermesRoutes.SETTINGS)
                },
                onNavigateToModels = {
                    navController.navigate(HermesRoutes.MODELS)
                }
            )
        }

        // Chat screen (existing conversation)
        composable(
            route = HermesRoutes.CHAT_WITH_ID,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")
            ChatScreen(
                conversationId = conversationId,
                onNavigateToConversations = {
                    navController.navigate(HermesRoutes.CONVERSATIONS)
                },
                onNavigateToSettings = {
                    navController.navigate(HermesRoutes.SETTINGS)
                },
                onNavigateToModels = {
                    navController.navigate(HermesRoutes.MODELS)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNewChat = {
                    navController.navigate(HermesRoutes.CHAT) {
                        popUpTo(HermesRoutes.CHAT) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Conversations list
        composable(HermesRoutes.CONVERSATIONS) {
            ConversationsScreen(
                onConversationSelected = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onNewChat = {
                    navController.navigate(HermesRoutes.CHAT) {
                        popUpTo(HermesRoutes.CHAT) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Model selector (full-screen route — accessed from SmartToy icon)
        composable(HermesRoutes.MODELS) {
            ModelSelectorScreen(
                currentModelId = null,
                onModelSelected = { model ->
                    // Pass model selection back to chat
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_model_id", model.id)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_model_provider", model.provider)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(HermesRoutes.PROVIDERS) }
            )
        }

        // Settings
        composable(HermesRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServerConnection = {
                    navController.navigate(HermesRoutes.SERVER_CONNECTION)
                },
                onNavigateToProviders = {
                    navController.navigate(HermesRoutes.PROVIDERS)
                }
            )
        }

        // Server connection
        composable(HermesRoutes.SERVER_CONNECTION) {
            ServerConnectionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Tasks
        composable(HermesRoutes.TASKS) {
            TasksScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // AI Providers configuration
        composable(HermesRoutes.PROVIDERS) {
            ProvidersScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
