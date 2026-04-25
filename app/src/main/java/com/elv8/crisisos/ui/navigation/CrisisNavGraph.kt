package com.elv8.crisisos.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.elv8.crisisos.ui.screens.discovery.PeerDiscoveryScreen
import com.elv8.crisisos.ui.screens.aiassistant.AiAssistantScreen
import com.elv8.crisisos.ui.screens.chat.ChatListScreen
import com.elv8.crisisos.ui.screens.chat.ChatThreadScreen
import com.elv8.crisisos.ui.screens.checkpoint.CheckpointScreen
import com.elv8.crisisos.ui.screens.fakenews.FakeNewsScreen
import com.elv8.crisisos.ui.screens.dangerzone.DangerZoneScreen
import com.elv8.crisisos.ui.screens.deconfliction.DeconflictionScreen
import com.elv8.crisisos.ui.screens.deadman.DeadManScreen
import com.elv8.crisisos.ui.screens.home.HomeScreen
import com.elv8.crisisos.ui.screens.home.MoreScreen
import com.elv8.crisisos.ui.screens.maps.MapsScreen
import com.elv8.crisisos.ui.screens.missingperson.MissingPersonScreen
import com.elv8.crisisos.ui.screens.sos.SosScreen
import com.elv8.crisisos.ui.screens.supply.SupplyScreen
import com.elv8.crisisos.ui.screens.chat.ChatHubScreen
import com.elv8.crisisos.ui.screens.news.CrisisNewsScreen
import com.elv8.crisisos.ui.screens.community.CommunityBoardScreen

@Composable
fun CrisisNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.Home.route) { 
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        } 
        composable(Screen.ChatHub.route) {
            ChatHubScreen(
                onNavigateToThread = { threadId -> navController.navigate(Screen.ChatThread().createRoute(threadId)) }
            )
        }
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onNavigateToThread = { threadId -> navController.navigate(Screen.ChatThread().createRoute(threadId)) },
                onNavigateToRequests = { navController.navigate(Screen.MessageRequests.route) },
                onNavigateToDiscover = { navController.navigate(Screen.PeerDiscovery.route) }
            )
        } 
        composable(
            route = Screen.ChatThread().route,
            arguments = listOf(androidx.navigation.navArgument("threadId") { type = androidx.navigation.NavType.StringType })
        ) { backStack ->
            val threadId = backStack.arguments?.getString("threadId") ?: return@composable
            ChatThreadScreen(
                threadId = threadId,
                onNavigateBack = { navController.popBackStack() },
                onTapAlias = { crsId ->
                    navController.navigate("peer_profile/$crsId?threadId=${threadId}&fromChat=true")
                }
            )
        }
        composable(
            route = Screen.Sos.route,
            enterTransition = { scaleIn(initialScale = 0.92f, animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)) },
            exitTransition = { scaleOut(targetScale = 0.92f, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { scaleIn(initialScale = 0.92f, animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)) },
            popExitTransition = { scaleOut(targetScale = 0.92f, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)) }
        ) {
            SosScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DeadManSwitch.route) { 
            DeadManScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.MissingPerson.route) { 
            MissingPersonScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(
            route = Screen.Supply().route,
            arguments = listOf(
                androidx.navigation.navArgument("category") { type = androidx.navigation.NavType.StringType; nullable = true },
                androidx.navigation.navArgument("notes") { type = androidx.navigation.NavType.StringType; nullable = true }
            )
        ) { backStack ->
            val category = backStack.arguments?.getString("category")
            val notes = backStack.arguments?.getString("notes")
            SupplyScreen(
                initialCategory = category,
                initialNotes = notes,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Maps.route) { 
            MapsScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(Screen.DangerZone.route) { 
            DangerZoneScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(Screen.Checkpoint.route) { 
            CheckpointScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(Screen.AiAssistant.route) { 
            AiAssistantScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFeature = { feature, params ->
                    when (feature) {
                        "SUPPLY_REQUEST" -> {
                            val cat = params["category"]
                            val note = params["notes"]
                            navController.navigate(Screen.Supply().createRoute(cat, note))
                        }
                        "OFFLINE_MAPS" -> navController.navigate(Screen.Maps.route)
                        "MISSING_PERSON" -> navController.navigate(Screen.MissingPerson.route)
                        "SOS" -> navController.navigate(Screen.Sos.route)
                        "CHECKPOINT_INTEL" -> navController.navigate(Screen.Checkpoint.route)
                        "CRISIS_NEWS" -> navController.navigate(Screen.CrisisNews.route)
                        else -> android.util.Log.w("AiNav", "Unknown feature: $feature")
                    }
                }
            )
        }
        composable(Screen.FakeNews.route) { 
            FakeNewsScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(Screen.Deconfliction.route) { 
            DeconflictionScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        composable(Screen.ChildAlert.route) {
            // Child Alert was merged into the unified CRS-ID lookup. Re-route legacy entry points
            // (notification deep-links, drawer history) to the Missing Person screen so users
            // land on the new Watches surface that absorbs the Child Alert flow.
            MissingPersonScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.CrisisNews.route) {
            CrisisNewsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.CommunityBoard.route) {
            CommunityBoardScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.PeerDiscovery.route) {
            PeerDiscoveryScreen(
                onNavigateToChat = { threadId -> navController.navigate(Screen.ChatThread().createRoute(threadId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.More.route) {
            MoreScreen(onNavigate = { route -> navController.navigate(route) })
        }

        composable(
            route = Screen.ConnectionRequest.route + "/{crsId}",
            arguments = listOf(androidx.navigation.navArgument("crsId") { type = androidx.navigation.NavType.StringType })
        ) { backStack ->
            val crsId = backStack.arguments?.getString("crsId") ?: return@composable
            com.elv8.crisisos.ui.screens.connection.SendConnectionRequestScreen(
                peerCrsId = crsId,
                onNavigateBack = { navController.popBackStack() },
                onRequestSent = { navController.popBackStack() }
            )
        }

        composable(Screen.Onboarding.route) {
            com.elv8.crisisos.ui.screens.onboarding.OnboardingScreen(onFinish = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

composable(Screen.MessageRequests.route) {
            com.elv8.crisisos.ui.screens.requests.MessageRequestsScreen(
                onNavigateToThread = { threadId -> navController.navigate(Screen.ChatThread().createRoute(threadId)) {
                    popUpTo(Screen.ChatHub.route)
                } },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Contacts.route) {
            com.elv8.crisisos.ui.screens.contacts.ContactsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PeerProfile.route,
            arguments = listOf(
                androidx.navigation.navArgument("crsId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("threadId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                androidx.navigation.navArgument("fromChat") { type = androidx.navigation.NavType.BoolType; defaultValue = false }
            )
        ) { backStack ->
            val crsId = backStack.arguments?.getString("crsId") ?: return@composable
            val threadId = backStack.arguments?.getString("threadId")
            val fromChat = backStack.arguments?.getBoolean("fromChat") ?: false
            com.elv8.crisisos.ui.screens.profile.PeerProfileScreen(
                crsId = crsId,
                threadId = threadId,
                isFromChat = fromChat,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFullscreenMedia = { mediaId -> navController.navigate("fullscreen_media/$mediaId") }
            )
        }

        composable(
            route = Screen.FullscreenMedia.route,
            arguments = listOf(
                androidx.navigation.navArgument("mediaId") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStack ->
            val mediaId = backStack.arguments?.getString("mediaId") ?: return@composable
            com.elv8.crisisos.ui.screens.profile.FullscreenMediaViewer(
                mediaId = mediaId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }
}


