package com.fplbp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fplbp.app.R
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.screens.ChipsScreen
import com.fplbp.app.ui.screens.DebtScreen
import com.fplbp.app.ui.screens.GameweekDetailScreen
import com.fplbp.app.ui.screens.GameweeksScreen
import com.fplbp.app.ui.screens.PlayersScreen
import com.fplbp.app.ui.screens.SettingsScreen
import com.fplbp.app.ui.screens.StatsScreen
import com.fplbp.app.ui.screens.TeamDetailScreen
import com.fplbp.app.ui.share.ShareImage
import com.fplbp.app.ui.theme.App
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab("dug", "Dug", R.drawable.ic_nav_debt),
    Tab("kola", "Kola", R.drawable.ic_nav_rounds),
    Tab("igraci", "Igrači", R.drawable.ic_nav_players),
    Tab("cipovi", "Čipovi", R.drawable.ic_nav_chips),
    Tab("statistika", "Statistika", R.drawable.ic_nav_stats),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val data by vm.data.collectAsState()
    val sync by vm.sync.collectAsState()
    val update by vm.update.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dug"
    val isTab = tabs.any { it.route == route }
    val context = LocalContext.current

    val title = when {
        route == "podesavanja" -> "Podešavanja"
        route.startsWith("tim") -> entry?.arguments?.getLong("id")?.let { data?.teamName(it) } ?: "Tim"
        route.startsWith("kolo") -> "Kolo ${entry?.arguments?.getInt("gw") ?: ""}"
        else -> data?.leagueName ?: "FPLBP"
    }

    val bgBrush = Brush.verticalGradient(
        0f to App.colors.bgTop,
        0.45f to MaterialTheme.colorScheme.background,
        1f to MaterialTheme.colorScheme.background,
    )
    var shareMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.background(bgBrush),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad")
                    }
                },
                actions = {
                    val d = data
                    if (isTab && d != null) Box {
                        IconButton(onClick = { shareMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Podeli sliku")
                        }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Tabela dugovanja") },
                                onClick = { shareMenu = false; ShareImage.shareDebtTable(context, d) },
                            )
                            ShareImage.shareableGw(d)?.let { gv ->
                                DropdownMenuItem(
                                    text = { Text("Kolo ${gv.gw}") },
                                    onClick = { shareMenu = false; ShareImage.shareGameweek(context, d, gv) },
                                )
                            }
                        }
                    }
                    if (isTab) IconButton(onClick = { nav.navigate("podesavanja") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Podešavanja")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(t.icon), contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(sync.running && data != null) {
                LinearProgressIndicator(progress = { sync.progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            if (sync.updating && data != null) {
                Banner(
                    color = App.colors.provisional,
                    text = "FPL se ažurira. Prikazani su sačuvani podaci od ${time(data!!.lastSync)}. Pokušavam ponovo automatski.",
                )
            } else if (sync.error != null && !sync.running && data != null) {
                Banner(color = App.colors.debt, text = "Nije uspelo osvežavanje (${sync.error}). Prikazani su sačuvani podaci.")
            }
            update?.let { u ->
                if (isTab) Banner(
                    color = App.colors.gold,
                    text = "Nova verzija dostupna.",
                    action = "Preuzmi" to { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url))) },
                )
            }

            val d = data
            if (d == null) {
                FirstLoad(loaded, sync.running, sync.progress, sync.message, sync.updating, sync.error, vm::refresh)
                return@Column
            }

            PullToRefreshBox(isRefreshing = sync.running, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = "dug", modifier = Modifier.fillMaxSize()) {
                    composable("dug") { DebtScreen(d, onTeam = { nav.navigate("tim/$it") }) }
                    composable("kola") { GameweeksScreen(d, onGw = { nav.navigate("kolo/$it") }) }
                    composable("igraci") { PlayersScreen(d) }
                    composable("cipovi") { ChipsScreen(d) }
                    composable("statistika") { StatsScreen(d, onTeam = { nav.navigate("tim/$it") }) }
                    composable("tim/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        TeamDetailScreen(d, it.arguments!!.getLong("id"), onGw = { gw -> nav.navigate("kolo/$gw") })
                    }
                    composable("kolo/{gw}", arguments = listOf(navArgument("gw") { type = NavType.IntType })) {
                        GameweekDetailScreen(d, it.arguments!!.getInt("gw"))
                    }
                    composable("podesavanja") { SettingsScreen(vm) }
                }
            }
        }
    }
}

@Composable
private fun Banner(color: Color, text: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun FirstLoad(
    loaded: Boolean, running: Boolean, progress: Float, message: String,
    updating: Boolean, error: String?, retry: () -> Unit,
) {
    if (!loaded) return
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FPLBP", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            when {
                running -> {
                    Text("Prvo učitavanje sezone…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(message, style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
                }
                updating -> {
                    EmptyState("FPL se trenutno ažurira (obično traje do sat vremena posle roka). Pokušaj ponovo malo kasnije.", Modifier.height(120.dp))
                    TextButton(onClick = retry) { Text("Pokušaj ponovo") }
                }
                else -> {
                    Text(error ?: "Nema podataka.", style = MaterialTheme.typography.bodyMedium, color = App.colors.muted)
                    TextButton(onClick = retry) { Text("Učitaj") }
                }
            }
        }
    }
}

fun time(ms: Long): String =
    if (ms == 0L) "—" else SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(ms))
