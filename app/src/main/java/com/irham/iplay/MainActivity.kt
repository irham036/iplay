package com.irham.iplay

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Bg = Color(0xFF0F0F0F)
val Chip = Color(0xFF272727)
val Red = Color(0xFFFF0033)
val Sub = Color(0xFFAAAAAA)

/** Kategori beranda. query = null berarti video populer (kelompok Umum). */
data class Cat(val label: String, val group: String, val query: String?)

val ALL_CATS = listOf(
    Cat("Kartun Edukasi", "anak", "kartun edukasi anak indonesia"),
    Cat("Lagu Anak", "anak", "lagu anak anak indonesia"),
    Cat("Dongeng", "anak", "dongeng anak sebelum tidur"),
    Cat("Belajar", "anak", "belajar membaca berhitung anak"),
    Cat("Sains Anak", "anak", "sains untuk anak"),
    Cat("Hewan", "anak", "video hewan untuk anak"),
    Cat("Jump Scare", "horor", "jump scare compilation"),
    Cat("Cerita Horor", "horor", "cerita horor"),
    Cat("Horor Indonesia", "horor", "film horor indonesia"),
    Cat("Film Pendek Horor", "horor", "horror short film"),
    Cat("Creepypasta", "horor", "creepypasta horror story"),
    Cat("Game Horor", "horor", "horror game jumpscare"),
    Cat("Populer", "umum", null),
    Cat("Musik", "umum", "musik indonesia terbaru"),
)

fun isTvDevice(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = isTvDevice(this)
        Parental.init(this, tv)
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
            ) { App(tv) }
        }
    }
}

/** Status satu daftar video. */
class Feed(private val loader: suspend (String?) -> Page) {
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
            val p = loader(if (reset) null else next)
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

fun feedFor(c: Cat): Feed = Feed { token ->
    if (c.query == null) YouTubeApi.popular(token)
    else YouTubeApi.search(c.query, token, setOf(c.group))
}

/** Bingkai sorotan untuk navigasi remot TV. */
@Composable
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    var f by remember { mutableStateOf(false) }
    val m = this.onFocusChanged { f = it.isFocused }
    return if (f) m.scale(1.04f).border(3.dp, Color.White, shape) else m
}

@Composable
fun App(tv: Boolean) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val store = remember { Store(ctx) }
    val feeds = remember { mutableMapOf<String, Feed>() }
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var searchFeed by remember { mutableStateOf<Feed?>(null) }
    var playing by remember { mutableStateOf<Video?>(null) }
    var queue by remember { mutableStateOf<List<Video>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    val groups = Parental.groups
    val cats = remember(groups) { ALL_CATS.filter { it.group in groups } }
    val catIdx = cat.coerceIn(0, (cats.size - 1).coerceAtLeast(0))
    LaunchedEffect(groups) { searchFeed = null }

    // Hitung waktu tonton (selama aplikasi tampil di layar) & sinkron ke VPS tiap menit.
    val watching by rememberUpdatedState(playing?.title)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) Parental.tick(1)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) Parental.sync(watching)
            delay(if (Parental.locked) 20_000 else 60_000)
        }
    }

    val play: (Video, List<Video>) -> Unit = { v, q -> playing = v; queue = q }

    if (Parental.locked) {
        LaunchedEffect(Unit) { playing = null }
        LockScreen(tv)
        return
    }

    val current = playing
    val homeFeed = cats.getOrNull(catIdx)?.let { c -> feeds.getOrPut(c.label) { feedFor(c) } }
    val submit: (String) -> Unit = { q ->
        if (q.isNotBlank()) {
            val g = groups
            val query = YouTubeApi.scopedQuery(q.trim(), g)
            searchFeed = Feed { token -> YouTubeApi.search(query, token, g) }
        }
    }
    when {
        current != null -> {
            if (tv) TvPlayer(current, store, onBack = { playing = null; refresh++ })
            else PlayerScreen(
                v = current, queue = queue, store = store,
                onPlay = { playing = it },
                onBack = { playing = null; refresh++ },
            )
        }
        searching -> {
            BackHandler { searching = false }
            SearchScreen(
                tv = tv,
                text = searchText,
                onText = { searchText = it },
                feed = searchFeed,
                onSubmit = submit,
                onBack = { searching = false },
                onPlay = play,
            )
        }
        tv -> TvHome(
            tab = tab, onTab = { tab = it; refresh++ }, onSearch = { searching = true },
            cats = cats, cat = catIdx, onCat = { cat = it }, feed = homeFeed,
            store = store, refresh = refresh, onPlay = play,
        )
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
                    0 -> HomeScreen(cats, catIdx, { cat = it }, homeFeed, play)
                    1 -> SavedScreen(false, store, Store.SAVED, refresh, "Belum ada video tersimpan", play)
                    else -> SavedScreen(false, store, Store.HISTORY, refresh, "Riwayat tontonan kosong", play)
                }
            }
        }
    }
}

@Composable
fun TimeBadge() {
    if (Parental.unlimited) return
    val m = (Parental.remainingSec + 59) / 60
    Text(
        "⏱ $m mnt",
        color = if (m <= 5) Color.White else Sub,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (m <= 5) Red else Chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun LockScreen(tv: Boolean) {
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tv) runCatching { fr.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo()
        Spacer(Modifier.height(32.dp))
        Text("🌙", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (Parental.lockNow) "Aplikasi sedang dikunci" else "Waktu nonton hari ini sudah habis",
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ayo istirahat, bermain, atau membaca buku dulu.\nMinta Ayah/Bunda kalau ingin menambah waktu.",
            color = Sub, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { scope.launch { Parental.sync(null) } },
            colors = ButtonDefaults.buttonColors(containerColor = Chip, contentColor = Color.White),
            modifier = Modifier.focusRequester(fr).focusRing(RoundedCornerShape(24.dp)),
        ) { Text("Cek lagi") }
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
        TimeBadge()
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

@Composable
fun CatChips(cats: List<Cat>, cat: Int, onCat: (Int) -> Unit, firstFocus: FocusRequester? = null) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(cats) { i, c ->
            val sel = i == cat
            Text(
                c.label,
                color = if (sel) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .focusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Color.White else Chip)
                    .clickable { onCat(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(cats: List<Cat>, cat: Int, onCat: (Int) -> Unit, feed: Feed?, onPlay: (Video, List<Video>) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        CatChips(cats, cat, onCat)
        if (feed != null) PullToRefreshBox(
            isRefreshing = feed.loading && feed.videos.isNotEmpty(),
            onRefresh = { scope.launchLoad(feed, reset = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            key(feed) { VideoList(feed, onPlay, 1) }
        }
    }
}

@Composable
fun TvTab(label: String, selected: Boolean, fr: FocusRequester? = null, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .focusRing(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White else Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
fun TvHome(
    tab: Int, onTab: (Int) -> Unit, onSearch: () -> Unit,
    cats: List<Cat>, cat: Int, onCat: (Int) -> Unit, feed: Feed?,
    store: Store, refresh: Int, onPlay: (Video, List<Video>) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 40.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo()
            Spacer(Modifier.width(20.dp))
            TvTab("Beranda", tab == 0, first) { onTab(0) }
            TvTab("Cari", false) { onSearch() }
            TvTab("Tersimpan", tab == 1) { onTab(1) }
            TvTab("Riwayat", tab == 2) { onTab(2) }
            Spacer(Modifier.weight(1f))
            TimeBadge()
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> {
                CatChips(cats, cat, onCat)
                if (feed != null) key(feed) { VideoList(feed, onPlay, 4) }
            }
            1 -> SavedScreen(true, store, Store.SAVED, refresh, "Belum ada video tersimpan", onPlay)
            else -> SavedScreen(true, store, Store.HISTORY, refresh, "Riwayat tontonan kosong", onPlay)
        }
    }
}

fun CoroutineScope.launchLoad(feed: Feed, reset: Boolean = false) {
    launch { feed.load(reset) }
}

@Composable
fun VideoList(feed: Feed, onPlay: (Video, List<Video>) -> Unit, cols: Int) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(feed) { if (!feed.loaded) feed.load() }

    val nearEnd by remember(feed) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - cols * 2
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = if (cols > 1) PaddingValues(vertical = 8.dp) else PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (cols > 1) 16.dp else 0.dp),
        ) {
            gridItems(feed.videos, key = { it.id }) { v ->
                VideoCard(v, rounded = cols > 1, onClick = { onPlay(v, feed.videos.toList()) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        feed.loading -> CircularProgressIndicator(color = Red, strokeWidth = 3.dp)
                        feed.error != null && feed.videos.isNotEmpty() -> ErrorBox(feed.error!!) { scope.launchLoad(feed) }
                        feed.loaded && feed.videos.isEmpty() -> Text("Tidak ada video ditemukan", color = Sub)
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
fun SearchScreen(
    tv: Boolean,
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
    val hint = when {
        "umum" in Parental.groups -> "Cari video"
        Parental.groups == setOf("horor") -> "Cari video horor"
        Parental.groups == setOf("anak") -> "Cari video anak"
        else -> "Cari video"
    }
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .then(if (tv) Modifier.padding(horizontal = 40.dp, vertical = 20.dp) else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
            }
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(hint, color = Sub) },
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
            IconButton(onClick = { kb?.hide(); onSubmit(text) }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.Filled.Search, "Cari", tint = Color.White)
            }
        }
        if (feed != null) key(feed) { VideoList(feed, onPlay, if (tv) 4 else 1) }
        else Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("Hanya kategori yang diizinkan orang tua yang ditampilkan", color = Sub, fontSize = 13.sp)
        }
    }
}

@Composable
fun SavedScreen(tv: Boolean, store: Store, key: String, refresh: Int, empty: String, onPlay: (Video, List<Video>) -> Unit) {
    var list by remember(key, refresh) { mutableStateOf(store.list(key)) }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(empty, color = Sub) }
        return
    }
    if (tv) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (key == Store.HISTORY) item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.End) {
                    TvTab("Hapus semua riwayat", false) { store.clear(key); list = emptyList() }
                }
            }
            gridItems(list, key = { it.id }) { v ->
                VideoCard(v, rounded = true, onClick = { onPlay(v, list) })
            }
        }
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

/** Pemutar khusus TV: layar penuh, dikendalikan tombol remot. */
@Composable
fun TvPlayer(v: Video, store: Store, onBack: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var cur by remember { mutableFloatStateOf(0f) }
    var dur by remember { mutableFloatStateOf(0f) }
    var poke by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    val fr = remember { FocusRequester() }

    LaunchedEffect(v.id) { store.add(Store.HISTORY, v); runCatching { fr.requestFocus() } }
    LaunchedEffect(poke) { showUi = true; delay(4000); showUi = false }
    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val p = player
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        if (ended) { p?.seekTo(0f); p?.play(); ended = false }
                        else if (isPlaying) p?.pause() else p?.play()
                        poke++; true
                    }
                    Key.MediaPlay -> { p?.play(); poke++; true }
                    Key.MediaPause -> { p?.pause(); poke++; true }
                    Key.DirectionLeft, Key.MediaRewind -> { p?.seekTo((cur - 10f).coerceAtLeast(0f)); poke++; true }
                    Key.DirectionRight, Key.MediaFastForward -> { p?.seekTo(cur + 10f); poke++; true }
                    Key.DirectionUp, Key.DirectionDown -> { poke++; true }
                    else -> false
                }
            }
    ) {
        key(v.id) {
            AndroidView(
                factory = { c ->
                    YouTubePlayerView(c).apply {
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        enableAutomaticInitialization = false
                        activity.lifecycle.addObserver(this)
                        val opts = IFramePlayerOptions.Builder(c)
                            .controls(0).rel(0).ivLoadPolicy(3).autoplay(1).build()
                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                player = youTubePlayer
                                youTubePlayer.loadVideo(v.id, 0f)
                            }

                            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                isPlaying = state == PlayerConstants.PlayerState.PLAYING ||
                                    state == PlayerConstants.PlayerState.BUFFERING
                                if (state == PlayerConstants.PlayerState.ENDED) { ended = true; poke++ }
                                if (state == PlayerConstants.PlayerState.PAUSED) poke++
                            }

                            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { cur = second }
                            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) { dur = duration }
                        }, opts)
                    }
                },
                onRelease = {
                    activity.lifecycle.removeObserver(it)
                    it.release()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showUi || !isPlaying) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                Text(v.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.channel, color = Sub, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (dur > 0f) (cur / dur).coerceIn(0f, 1f) else 0f },
                    color = Red, trackColor = Color(0x55FFFFFF),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "${fmtSec(cur)} / ${fmtSec(dur)}   " + when {
                            ended -> "Selesai — OK: putar ulang"
                            isPlaying -> "▶ Diputar"
                            else -> "❚❚ Jeda"
                        },
                        color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    Text("OK: putar/jeda   ◀ ▶: 10 dtk   BACK: kembali", color = Sub, fontSize = 14.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(24.dp)) { TimeBadge() }
    }
}

fun fmtSec(s: Float): String {
    val t = s.toInt().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
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
fun VideoCard(v: Video, rounded: Boolean = false, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).focusRing().clickable(onClick = onClick)) {
        Thumb(
            v,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .then(if (rounded) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
        )
        Row(Modifier.padding(start = if (rounded) 0.dp else 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
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
        Modifier.fillMaxWidth().focusRing().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                TimeBadge()
            }
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
