package com.irham.iplay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

val Bg = Color(0xFF0F0F0F)
val Chip = Color(0xFF272727)
val Red = Color(0xFFFF0033)
val Sub = Color(0xFFAAAAAA)

/** Kategori beranda: label ke kata kunci pencarian (semuanya horor). */
val CATEGORIES = listOf(
    "Semua" to "horror jump scare",
    "Jump Scare" to "jump scare compilation",
    "Cerita Horor" to "cerita horor",
    "Horor Indonesia" to "film horor indonesia",
    "Film Pendek" to "horror short film",
    "Creepypasta" to "creepypasta horror story",
    "Game Horor" to "horror game jumpscare",
    "Kisah Nyata" to "kisah nyata horor mistis",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red, background = Bg, surface = Bg,
                    onBackground = Color.White, onSurface = Color.White,
                )
            ) { App() }
        }
    }
}

/** Status satu daftar video (feed kategori atau hasil pencarian). */
class Feed(val query: String) {
    val videos = mutableStateListOf<Video>()
    var next by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var pages = 0

    suspend fun load(reset: Boolean = false) {
        if (loading) return
        if (!reset && loaded && next == null) return
        loading = true
        error = null
        try {
            val p = YouTubeApi.search(query, if (reset) null else next)
            if (reset) { videos.clear(); pages = 0 }
            videos.addAll(p.videos.filter { v -> videos.none { it.id == v.id } })
            next = p.next
            pages++
            loaded = true
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat video"
        } finally {
            loading = false
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val feeds = remember { CATEGORIES.map { Feed(it.second) } }
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var searchFeed by remember { mutableStateOf<Feed?>(null) }
    var playing by remember { mutableStateOf<Video?>(null) }
    var queue by remember { mutableStateOf<List<Video>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    val play: (Video, List<Video>) -> Unit = { v, q -> playing = v; queue = q }

    val current = playing
    when {
        current != null -> {
            PlayerScreen(
                v = current, queue = queue, store = store,
                onPlay = { playing = it },
                onBack = { playing = null; refresh++ },
            )
        }
        searching -> {
            BackHandler { searching = false }
            SearchScreen(
                text = searchText,
                onText = { searchText = it },
                feed = searchFeed,
                onSubmit = { q -> if (q.isNotBlank()) searchFeed = Feed(YouTubeApi.horrorQuery(q.trim())) },
                onBack = { searching = false },
                onPlay = play,
            )
        }
        else -> Scaffold(
            containerColor = Bg,
            topBar = { TopBar(onSearch = { searching = true }) },
            bottomBar = {
                NavigationBar(containerColor = Bg) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White, selectedTextColor = Color.White,
                        unselectedIconColor = Sub, unselectedTextColor = Sub, indicatorColor = Chip,
                    )
                    listOf(
                        Triple("Beranda", Icons.Filled.Home, 0),
                        Triple("Tersimpan", Icons.Filled.VideoLibrary, 1),
                        Triple("Riwayat", Icons.Filled.History, 2),
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i; refresh++ },
                            icon = { Icon(icon, null) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = colors,
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(cat, { cat = it }, feeds[cat], play)
                    1 -> SavedScreen(store, Store.SAVED, refresh, "Belum ada video tersimpan", play)
                    else -> SavedScreen(store, Store.HISTORY, refresh, "Riwayat tontonan kosong", play)
                }
            }
        }
    }
}

@Composable
fun TopBar(onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Cari", tint = Color.White) }
    }
}

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 30.dp, height = 21.dp).clip(RoundedCornerShape(6.dp)).background(Red),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(4.dp))
        Text("iPlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.5).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(cat: Int, onCat: (Int) -> Unit, feed: Feed, onPlay: (Video, List<Video>) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(CATEGORIES) { i, (label, _) ->
                val sel = i == cat
                Text(
                    label,
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Color.White else Chip)
                        .clickable { onCat(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = feed.loading && feed.videos.isNotEmpty(),
            onRefresh = { scope.launchLoad(feed, reset = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            key(feed) { VideoList(feed, onPlay) }
        }
    }
}

fun CoroutineScope.launchLoad(feed: Feed, reset: Boolean = false) {
    launch { feed.load(reset) }
}

@Composable
fun VideoList(feed: Feed, onPlay: (Video, List<Video>) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(feed) { if (!feed.loaded) feed.load() }

    val nearEnd by remember(feed) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && feed.loaded && !feed.loading && feed.error == null) feed.load()
    }
    // Jika hasil terlalu sedikit setelah difilter, ambil halaman berikutnya (maks 3 halaman otomatis).
    LaunchedEffect(feed.videos.size, feed.loading) {
        if (feed.loaded && !feed.loading && feed.error == null && feed.videos.size < 6 &&
            feed.next != null && feed.pages < 3
        ) feed.load()
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(feed.videos, key = { it.id }) { v ->
            VideoCard(v, onClick = { onPlay(v, feed.videos.toList()) })
        }
        item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    feed.loading -> CircularProgressIndicator(color = Red, strokeWidth = 3.dp)
                    feed.error != null && feed.videos.isNotEmpty() -> ErrorBox(feed.error!!) { scope.launchLoad(feed) }
                    feed.loaded && feed.videos.isEmpty() -> Text("Tidak ada video horor ditemukan", color = Sub)
                }
            }
        }
    }
    val err = feed.error
    if (err != null && feed.videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorBox(err) { scope.launchLoad(feed, reset = true) }
        }
    }
    }
}

@Composable
fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(msg, color = Sub, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Coba lagi", color = Color.White) }
    }
}

fun meta(v: Video) = listOf(v.channel, v.views, YouTubeApi.ago(v.published))
    .filter { it.isNotBlank() }.joinToString(" • ")

@Composable
fun Thumb(v: Video, modifier: Modifier) {
    Box(modifier.background(Color(0xFF222222))) {
        AsyncImage(
            model = v.thumb, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        if (v.duration.isNotEmpty()) Text(
            v.duration, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun Avatar(name: String, size: Int = 36) {
    val palette = listOf(0xFF7B1FA2, 0xFFC62828, 0xFF2E7D32, 0xFF1565C0, 0xFFEF6C00, 0xFF4E342E)
    val c = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.3).sp)
    }
}

@Composable
fun VideoCard(v: Video, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 12.dp)) {
        Thumb(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp)) {
            Avatar(v.channel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(meta(v), color = Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun CompactCard(v: Video, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Thumb(v, Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(meta(v), color = Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun SearchScreen(
    text: String,
    onText: (String) -> Unit,
    feed: Feed?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Video, List<Video>) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (feed == null) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Cari video horor", color = Sub) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Red,
                ),
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { onText("") }) {
                        Icon(Icons.Filled.Close, "Hapus", tint = Sub)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { kb?.hide(); onSubmit(text) }),
                modifier = Modifier.weight(1f).height(52.dp).focusRequester(focus),
            )
            IconButton(onClick = { kb?.hide(); onSubmit(text) }) {
                Icon(Icons.Filled.Search, "Cari", tint = Color.White)
            }
        }
        if (feed != null) key(feed) { VideoList(feed, onPlay) }
        else Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("Hanya konten horor & jump scare yang ditampilkan", color = Sub, fontSize = 13.sp)
        }
    }
}

@Composable
fun SavedScreen(store: Store, key: String, refresh: Int, empty: String, onPlay: (Video, List<Video>) -> Unit) {
    var list by remember(key, refresh) { mutableStateOf(store.list(key)) }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(empty, color = Sub) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (key == Store.HISTORY) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { store.clear(key); list = emptyList() }) { Text("Hapus semua", color = Red) }
            }
        }
        items(list, key = { it.id }) { v ->
            CompactCard(v, onClick = { onPlay(v, list) }, trailing = {
                IconButton(onClick = { store.remove(key, v.id); list = store.list(key) }) {
                    Icon(Icons.Filled.Close, "Hapus", tint = Sub)
                }
            })
        }
    }
}

@Composable
fun PlayerScreen(v: Video, queue: List<Video>, store: Store, onPlay: (Video) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    var saved by remember(v.id) { mutableStateOf(store.has(Store.SAVED, v.id)) }
    var fullView by remember { mutableStateOf<View?>(null) }
    var exitFull by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(v.id) { store.add(Store.HISTORY, v) }

    fun setFullscreen(on: Boolean) {
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (on) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) { onDispose { setFullscreen(false) } }

    BackHandler {
        val ex = exitFull
        if (fullView != null && ex != null) ex() else onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            key(v.id) {
                AndroidView(
                    factory = { c ->
                        YouTubePlayerView(c).apply {
                            enableAutomaticInitialization = false
                            activity.lifecycle.addObserver(this)
                            addFullscreenListener(object : FullscreenListener {
                                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                                    fullView = fullscreenView
                                    exitFull = exitFullscreen
                                    setFullscreen(true)
                                }

                                override fun onExitFullscreen() {
                                    fullView = null
                                    exitFull = null
                                    setFullscreen(false)
                                }
                            })
                            val opts = IFramePlayerOptions.Builder(c)
                                .controls(1).fullscreen(1).rel(0).ivLoadPolicy(3).autoplay(1)
                                .build()
                            initialize(object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    youTubePlayer.loadVideo(v.id, 0f)
                                }
                            }, opts)
                        }
                    },
                    onRelease = {
                        activity.lifecycle.removeObserver(it)
                        it.release()
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOf(v.views, YouTubeApi.ago(v.published)).filter { it.isNotBlank() }.joinToString(" • "),
                            color = Sub, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(v.channel, 40)
                            Spacer(Modifier.width(10.dp))
                            Text(v.channel, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChip(
                                if (saved) "Tersimpan" else "Simpan",
                                if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            ) {
                                if (saved) store.remove(Store.SAVED, v.id) else store.add(Store.SAVED, v, 1000)
                                saved = !saved
                            }
                            ActionChip("Bagikan", Icons.Filled.Share) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${v.title}\nhttps://youtu.be/${v.id}")
                                }
                                ctx.startActivity(Intent.createChooser(send, "Bagikan"))
                            }
                        }
                    }
                    HorizontalDivider(color = Chip)
                    Text("Video horor lainnya", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                }
                items(queue.filter { it.id != v.id }, key = { it.id }) { o ->
                    CompactCard(o, onClick = { onPlay(o) })
                }
            }
        }
        val fv = fullView
        if (fv != null) {
            AndroidView(
                factory = { c ->
                    FrameLayout(c).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (fv.parent as? ViewGroup)?.removeView(fv)
                        addView(fv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
