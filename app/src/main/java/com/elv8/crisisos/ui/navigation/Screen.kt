package com.elv8.crisisos.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ChatHub : Screen("chat_hub")
    data object ChatList : Screen("chat_list")
    data class ChatThread(val threadId: String = "{threadId}") : Screen("chat_thread/$threadId") {
        fun createRoute(id: String) = "chat_thread/$id"
    }
    data object Sos : Screen("sos")
    data object DeadManSwitch : Screen("dead_man_switch")
    data object MissingPerson : Screen("missing_person")
    data class Supply(val category: String? = null, val notes: String? = null) : Screen("supply?category={category}&notes={notes}") {
        fun createRoute(cat: String? = null, note: String? = null) = "supply?category=$cat&notes=$note"
    }
    data object Maps : Screen("maps")
    data object DangerZone : Screen("danger_zone")
    data object Checkpoint : Screen("checkpoint")
    data object AiAssistant : Screen("ai_assistant")
    data object FakeNews : Screen("fake_news")
    data object Deconfliction : Screen("deconfliction")
    data object ChildAlert : Screen("child_alert")
    data object CrisisNews : Screen("crisis_news")
    data object CommunityBoard : Screen("community_board")
    data object More : Screen("more")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
    data object PeerDiscovery : Screen("peer_discovery")
    data object ConnectionRequest : Screen("connection_request")
    data object IncomingRequests : Screen("incoming_requests")
    data object MessageRequests : Screen("message_requests")
    data object Contacts : Screen("contacts")
    data object PeerProfile : Screen("peer_profile/{crsId}?threadId={threadId}&fromChat={fromChat}")
    data object FullscreenMedia : Screen("fullscreen_media/{mediaId}")
}

