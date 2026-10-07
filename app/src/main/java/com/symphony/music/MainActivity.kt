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
import androidx.compose.animation.slideInVertically
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
import com.symphony.music.data.Song
import com.symphony.music.ui.AlbumScreen
import com.symphony.music.ui.AlbumsScreen
import com.symphony.music.ui.ArtistScreen
import com.symphony.music.ui.ArtistsScreen
import com.symphony.music.ui.FloatingBar
import com.symphony.music.ui.HomeScreen
import com.symphony.music.ui.LibraryScreen
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
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val openAlbum: (Long) -> Unit = { nav.navigate("album/$it") }
    val openArtist: (String) -> Unit = { nav.navigate("artist/" + Uri.encode(it)) }
    val openPlaylist: (String) -> Unit = { nav.navigate("playlist/" + Uri.encode(it)) }
    val openMenu: (Song) -> Unit = { menuSong = it }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") { HomeScreen(vm, { nav.navigate("settings") }, openAlbum, openMenu) }
            composable("albums") { AlbumsScreen(vm, openAlbum) }
            composable("artists") { ArtistsScreen(vm, openArtist) }
            composable("library") { LibraryScreen(vm, openPlaylist, openMenu) }
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

        FloatingBar(
            state = state,
            glass = settings.liquidGlass,
            route = route,
            onTab = { nav.openTab(it) },
            onSearch = { nav.openTab("search") },
            onOpenPlayer = { playerOpen = true },
            onToggle = { vm.toggle() },
            onNext = { vm.next() },
            modifier = Modifier.align(Alignment.BottomCenter),
            hideLabels = settings.hideLabels,
            classic = settings.classicBar,
        )

        AnimatedVisibility(
            visible = playerOpen && state.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
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
    val favorite = song.id in settings.favorites

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            MenuItem(Icons.Rounded.SkipNext, stringResource(R.string.play_next)) {
                vm.playNext(song)
                onDismiss()
            }
            MenuItem(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                text = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
            ) {
                vm.toggleFavorite(song.id)
                onDismiss()
            }
            MenuItem(Icons.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) { picking = true }
            MenuItem(Icons.Rounded.Album, stringResource(R.string.go_to_album), onAlbum)
            MenuItem(Icons.Rounded.Person, stringResource(R.string.go_to_artist), onArtist)
        }
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
