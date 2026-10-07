package com.holaoluwakintan.ranti.ui

import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed interface Route {
    data object Home : Route
    data object People : Route
    data object Settings : Route
    data object Onboarding : Route
    data object Import : Route
    data object Link : Route
    data class Edit(val id: Long = 0L, val capture: Boolean = false) : Route
    data class Detail(val id: Long) : Route
    data class Message(val id: Long) : Route
    /** v1.0 */
    data class QuickAdd(val text: String = "") : Route
    data object Reminders : Route
    data class ReminderEdit(val id: Long = 0L) : Route
}

class Nav(private val stack: SnapshotStateList<Route>) {
    val current: Route get() = stack.last()
    val canBack: Boolean get() = stack.size > 1
    fun push(r: Route) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(r: Route) { stack.clear(); stack.add(r) }
    fun replace(r: Route) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(r) }
}

@Composable
fun RantiRoot(vm: AppViewModel, pendingOpen: MutableState<Pair<String, Long>?>) {
    val start: Route = if (vm.prefs.onboarded) Route.Home else Route.Onboarding
    val stack = remember { mutableStateListOf(start) }
    val nav = remember { Nav(stack) }

    // Deep links from notifications.
    val open = pendingOpen.value
    LaunchedEffect(open) {
        if (open != null && vm.prefs.onboarded) {
            val (target, id) = open
            when (target) {
                "capture" -> { nav.tab(Route.Home); nav.push(Route.Edit(capture = true)) }
                "import" -> { nav.tab(Route.Home); nav.push(Route.Import) }
                "people" -> nav.tab(Route.People)
                "link" -> { nav.tab(Route.Home); nav.push(Route.Link) }
                "person" -> if (id > 0) { nav.tab(Route.Home); nav.push(Route.Detail(id)) }
                "message" -> if (id > 0) { nav.tab(Route.Home); nav.push(Route.Detail(id)); nav.push(Route.Message(id)) }
                "reminders" -> nav.tab(Route.Reminders)
                "quickadd" -> { nav.tab(Route.Home); nav.push(Route.QuickAdd()) }
                else -> nav.tab(Route.Home)
            }
            pendingOpen.value = null
        }
    }

    BackHandler(enabled = nav.canBack) { nav.back() }

    // v1.0: status-bar icons follow the app theme (not only the system one).
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    val dark = C.dark
    LaunchedEffect(dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
    }

    // v1.0: make sure reminders can actually show (Android 13+), asked once after onboarding.
    val cur = nav.current
    NotificationAsk(vm, cur)

    val isTab = cur == Route.Home || cur == Route.People || cur == Route.Settings || cur == Route.Reminders
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AnimatedContent(
            targetState = cur,
            transitionSpec = {
                val tabs = setOf(Route.Home, Route.People, Route.Settings, Route.Reminders)
                if (targetState in tabs && initialState in tabs) fadeIn(androidx.compose.animation.core.tween(180)) togetherWith fadeOut(androidx.compose.animation.core.tween(120))
                else (fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 14 }) togetherWith fadeOut(androidx.compose.animation.core.tween(140))
            },
            label = "nav",
        ) { r ->
            when (r) {
                Route.Home -> HomeScreen(vm, nav)
                Route.People -> PeopleScreen(vm, nav)
                Route.Settings -> SettingsScreen(vm, nav)
                Route.Onboarding -> OnboardingScreen(vm) { vm.prefs.onboarded = true; nav.tab(Route.Home) }
                Route.Import -> ImportScreen(vm, nav)
                Route.Link -> LinkScreen(vm, nav)
                is Route.Edit -> EditScreen(vm, nav, r.id, r.capture)
                is Route.Detail -> DetailScreen(vm, nav, r.id)
                is Route.Message -> MessageScreen(vm, nav, r.id)
                is Route.QuickAdd -> QuickAddScreen(vm, nav, r.text)
                Route.Reminders -> RemindersScreen(vm, nav)
                is Route.ReminderEdit -> ReminderEditScreen(vm, nav, r.id)
            }
        }
        if (isTab) BottomBar(cur, nav, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun BottomBar(cur: Route, nav: Nav, modifier: Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(if (C.dark) 0.dp else 16.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x332A1640), spotColor = Color(0x332A1640))
                .clip(RoundedCornerShape(24.dp)).background(C.Card)
                .then(if (C.dark) Modifier.border(1.dp, C.Line, RoundedCornerShape(24.dp)) else Modifier)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("Today", Icons.Filled.Home, cur == Route.Home, Modifier.weight(1f)) { nav.tab(Route.Home) }
            TabItem("People", Icons.Filled.Person, cur == Route.People, Modifier.weight(1f)) { nav.tab(Route.People) }
            Spacer(Modifier.weight(1f))
            TabItem("Reminders", Icons.Filled.Notifications, cur == Route.Reminders, Modifier.weight(1f)) { nav.tab(Route.Reminders) }
            TabItem("Settings", Icons.Filled.Settings, cur == Route.Settings, Modifier.weight(1f)) { nav.tab(Route.Settings) }
        }
        Box(
            Modifier.align(Alignment.Center).offset(y = (-14).dp).size(62.dp)
                .shadow(12.dp, CircleShape, ambientColor = C.Coral, spotColor = C.Coral)
                .clip(CircleShape).background(Sunset).clickable(onClickLabel = "Quick add") { nav.push(Route.QuickAdd()) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Add a birthday or reminder", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(if (selected) C.Coral else C.InkFaint, label = "tab")
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) C.Ink else C.InkFaint)
    }
}

/** v1.0: asks for the notification permission once (Android 13+) when the user is past onboarding. */
@Composable
fun NotificationAsk(vm: AppViewModel, key: Any) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        vm.reschedule()
    }
    LaunchedEffect(key, vm.resumeTick) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (vm.prefs.onboarded && !granted && !vm.prefs.notifAsked) {
            vm.prefs.notifAsked = true
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
