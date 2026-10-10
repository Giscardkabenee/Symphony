package com.symphony.music

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.symphony.music.data.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.symphony.music.data.Song
import com.symphony.music.ui.AlbumScreen
import com.symphony.music.ui.ArtistScreen
import com.symphony.music.ui.FloatingBar
import com.symphony.music.ui.HomeScreen
import com.symphony.music.ui.MusicScreen
import com.symphony.music.ui.LocalHaze
import com.symphony.music.ui.NewPlaylistDialog
import com.symphony.music.ui.NowPlaying
import com.symphony.music.ui.PlaylistScreen
import com.symphony.music.ui.SearchScreen
import com.symphony.music.ui.SettingsScreen
import com.symphony.music.ui.SymphonyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SymphonyApp() }
    }
}

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun SymphonyApp(vm: PlayerViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(granted) {
        if (granted) {
            vm.refresh()
            // Android 13+: lets the playback notification appear in the shade.
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Coming back to the app looks for songs downloaded in the meantime.
    androidx.lifecycle.compose.LifecycleResumeEffect(granted) {
        if (granted) vm.onResume()
        onPauseOrDispose { }
    }

    SymphonyTheme(settings.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (granted) {
                MainScaffold(vm, settings)
            } else {
                PermissionScreen { launcher.launch(audioPermission) }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.permission_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant), fontWeight = FontWeight.Bold) }
    }
}

private fun NavHostController.openTab(route: String) {
    // A tab already in the stack (Home always is): go back to its root page instead of
    // restoring the album or playlist that was open on top of it.
    val inStack = try {
        getBackStackEntry(route)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
    if (inStack) {
        popBackStack(route, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainScaffold(vm: PlayerViewModel, settings: AppSettings) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val hazeState = remember { HazeState() }
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val openAlbum: (Long) -> Unit = { nav.navigate("album/$it") }
    val openArtist: (String) -> Unit = { nav.navigate("artist/" + Uri.encode(it)) }
    val openPlaylist: (String) -> Unit = { nav.navigate("playlist/" + Uri.encode(it)) }
    val openMenu: (Song) -> Unit = { menuSong = it }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(com.symphony.music.ui.LocalIsPlaying provides state.isPlaying) {
        // Tabs cross-fade quickly; detail pages slide in from the right and back out the same way.
        val tabRoutes = setOf("home", "music", "search")
        fun between(a: String?, b: String?) = a in tabRoutes && b in tabRoutes
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            enterTransition = {
                if (between(initialState.destination.route, targetState.destination.route)) fadeIn(tween(200))
                else slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(260))
            },
            exitTransition = {
                if (between(initialState.destination.route, targetState.destination.route)) fadeOut(tween(160))
                else slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 12 } + fadeOut(tween(220))
            },
            popEnterTransition = {
                if (between(initialState.destination.route, targetState.destination.route)) fadeIn(tween(200))
                else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 12 } + fadeIn(tween(240))
            },
            popExitTransition = {
                if (between(initialState.destination.route, targetState.destination.route)) fadeOut(tween(160))
                else slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(200))
            },
        ) {
            composable("home") { HomeScreen(vm, { nav.navigate("settings") }, openPlaylist, openAlbum, openArtist) }
            composable("music") { MusicScreen(vm, openAlbum, openArtist, openPlaylist, openMenu) }
            composable("search") { SearchScreen(vm, openAlbum, openArtist, openMenu) }
            composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
            composable("album/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AlbumScreen(vm, e.arguments?.getLong("id") ?: 0L, { nav.popBackStack() }, openMenu)
            }
            composable("artist/{name}") { e ->
                ArtistScreen(vm, e.arguments?.getString("name") ?: "", { nav.popBackStack() }, openAlbum, openMenu)
            }
            composable("playlist/{name}") { e ->
                PlaylistScreen(vm, e.arguments?.getString("name") ?: "", { nav.popBackStack() }, openMenu)
            }
        }
        }

        // Settings is a full page of its own: no player bar, no tabs.
        if (route != "settings") CompositionLocalProvider(LocalHaze provides hazeState) {
        FloatingBar(
            state = state,
            glass = settings.liquidGlass,
            route = route,
            onTab = { nav.openTab(it) },
            onSearch = { nav.openTab("search") },
            onOpenPlayer = { playerOpen = true },
            onToggle = { vm.toggle() },
            onNext = { vm.next() },
            onPrevious = { vm.previous() },
            modifier = Modifier.align(Alignment.BottomCenter),
            hideLabels = settings.hideLabels,
            classic = settings.classicBar,
        )
        }

        AnimatedVisibility(
            visible = playerOpen && state.current != null,
            // The player rises on a soft spring and drops away a little faster.
            enter = slideInVertically(spring(dampingRatio = 0.92f, stiffness = 420f)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220, delayMillis = 60)),
        ) {
            NowPlaying(
                vm = vm,
                onClose = { playerOpen = false },
                onMore = openMenu,
                onArtist = {
                    playerOpen = false
                    openArtist(it)
                },
            )
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    menuSong?.let { song ->
        SongMenu(
            song = song,
            settings = settings,
            vm = vm,
            onDismiss = { menuSong = null },
            onAlbum = {
                menuSong = null
                playerOpen = false
                openAlbum(song.albumId)
            },
            onArtist = {
                menuSong = null
                playerOpen = false
                openArtist(song.artist)
            },
        )
    }
}

@Composable
private fun SongMenu(
    song: Song,
    settings: AppSettings,
    vm: PlayerViewModel,
    onDismiss: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val local = song.id >= 0
    val favorite = song.id in settings.favorites

    val context = LocalContext.current
    val nextLabel = stringResource(R.string.queued_next)
    val queueLabel = stringResource(R.string.queued_end)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        com.symphony.music.ui.SongSheet(
            song = song,
            favorite = favorite,
            onFavorite = { vm.toggleFavorite(song.id) },
            onPlayNext = {
                vm.playNext(song)
                android.widget.Toast.makeText(context, String.format(nextLabel, song.title), android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onQueue = {
                vm.addToQueue(song)
                android.widget.Toast.makeText(context, String.format(queueLabel, song.title), android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onPlaylist = { picking = true },
            onAlbum = onAlbum,
            onArtist = onArtist,
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                Column {
                    settings.playlists.keys.forEach { name ->
                        MenuItem(Icons.Rounded.QueueMusic, name) {
                            vm.addToPlaylist(name, song.id)
                            picking = false
                            onDismiss()
                        }
                    }
                    MenuItem(Icons.Rounded.Add, stringResource(R.string.new_playlist)) { creating = true }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) { name ->
            vm.createPlaylist(name)
            vm.addToPlaylist(name, song.id)
            creating = false
            picking = false
            onDismiss()
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
