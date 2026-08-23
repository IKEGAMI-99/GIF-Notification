package com.ikegami99.gifnotification

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GifNotificationApp() }
    }
}

private enum class Tab(val label: String, val icon: String) {
    Home("ホーム", "⌂"),
    Favorites("お気に入り", "♥"),
    History("履歴", "◷"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GifNotificationApp() {
    val context = LocalContext.current
    val store = remember { GifStore(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(Tab.Home) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var apiKeySaved by remember { mutableStateOf(store.apiKey.isNotBlank()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var history by remember { mutableStateOf(store.history()) }
    var selected by remember { mutableStateOf<GifItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingNotification by remember { mutableStateOf<GifItem?>(null) }

    val notify: (GifItem) -> Unit = { item ->
        scope.launch {
            runCatching {
                GifNotificationManager.show(context, item)
            }.onSuccess {
                store.addHistory(item)
                history = store.history()
                snackbar.showSnackbar("通知にGIFを表示しました")
                selected = null
            }.onFailure {
                snackbar.showSnackbar("通知の作成に失敗しました: ${it.message ?: "不明なエラー"}")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pendingNotification
        pendingNotification = null
        if (granted && item != null) notify(item)
        else scope.launch { snackbar.showSnackbar("通知権限が必要です") }
    }

    fun requestNotification(item: GifItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotification = item
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notify(item)
        }
    }

    fun runSearch(term: String) {
        if (apiKey.isBlank()) {
            scope.launch { snackbar.showSnackbar("先にGIPHY APIキーを保存してください") }
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching { GiphyClient.search(apiKey.trim(), term) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "検索に失敗しました" }
            loading = false
        }
    }

    val dark = false
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        else -> lightColorScheme(primary = Color(0xFF7357FF))
    }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.icon, fontWeight = FontWeight.Bold) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                Tab.Home -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    apiKey = apiKey,
                    apiKeySaved = apiKeySaved,
                    onApiKeyChange = { apiKey = it },
                    onSaveApiKey = {
                        store.apiKey = apiKey
                        apiKeySaved = apiKey.isNotBlank()
                        if (apiKeySaved) runSearch("")
                        scope.launch { snackbar.showSnackbar("APIキーを端末内に保存しました") }
                    },
                    onEditApiKey = { apiKeySaved = false },
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { runSearch(query) },
                    onCategory = { category -> query = category; runSearch(category) },
                    results = results,
                    loading = loading,
                    error = error,
                    onSelect = { selected = it },
                    onCancelNotification = {
                        GifNotificationManager.cancel(context)
                        scope.launch { snackbar.showSnackbar("GIF通知を停止しました") }
                    },
                )
                Tab.Favorites -> GifCollectionScreen(
                    Modifier.padding(padding),
                    "お気に入り",
                    favorites,
                    "お気に入りのGIFはまだありません",
                    onSelect = { selected = it },
                )
                Tab.History -> GifCollectionScreen(
                    Modifier.padding(padding),
                    "履歴",
                    history,
                    "通知に表示したGIFはまだありません",
                    onSelect = { selected = it },
                )
            }
        }

        selected?.let { item ->
            ModalBottomSheet(onDismissRequest = { selected = null }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(item.title.ifBlank { "GIF" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        RemoteGif(item.originalUrl, Modifier.fillMaxSize())
                    }
                    Button(
                        onClick = { requestNotification(item) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("通知に表示") }
                    OutlinedButton(
                        onClick = {
                            store.toggleFavorite(item)
                            favorites = store.favorites()
                            scope.launch {
                                snackbar.showSnackbar(if (store.isFavorite(item)) "お気に入りに追加しました" else "お気に入りから削除しました")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (favorites.any { it.id == item.id }) "♥ お気に入り済み" else "♡ お気に入り")
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (store.apiKey.isNotBlank() && results.isEmpty()) runSearch("")
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    apiKey: String,
    apiKeySaved: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onEditApiKey: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCategory: (String) -> Unit,
    results: List<GifItem>,
    loading: Boolean,
    error: String?,
    onSelect: (GifItem) -> Unit,
    onCancelNotification: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GIF検索 ✦", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("お気に入りの動きを通知センターへ", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    if (apiKeySaved) TextButton(onClick = onEditApiKey) { Text("APIキー") }
                    TextButton(onClick = onCancelNotification) { Text("通知停止") }
                }
            }

            if (!apiKeySaved) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GIPHY APIキー", fontWeight = FontWeight.Bold)
                        Text("キーは端末内だけに保存します。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                            singleLine = true,
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onSaveApiKey, modifier = Modifier.fillMaxWidth()) { Text("保存") }
                    }
                }
            }

            if (apiKeySaved) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("GIFを検索") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onSearch, modifier = Modifier.height(56.dp)) { Text("検索") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("リアクション", "かわいい", "面白い").forEach { category ->
                        FilterChip(
                            selected = query == category,
                            onClick = { onCategory(category) },
                            label = { Text(category) },
                        )
                    }
                }
                Text("Powered by GIPHY", style = MaterialTheme.typography.labelSmall)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            !apiKeySaved -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("APIキーを保存するとGIFを検索できます")
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("GIFが見つかりません") }
            else -> GifGrid(results, onSelect)
        }
    }
}

@Composable
private fun GifCollectionScreen(
    modifier: Modifier,
    title: String,
    gifs: List<GifItem>,
    emptyText: String,
    onSelect: (GifItem) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Text(
            title,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (gifs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyText) }
        } else {
            GifGrid(gifs, onSelect)
        }
    }
}

@Composable
private fun GifGrid(gifs: List<GifItem>, onSelect: (GifItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(gifs, key = { it.id }) { gif ->
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onSelect(gif) },
                shape = RoundedCornerShape(18.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    RemoteGif(gif.previewUrl, Modifier.fillMaxSize())
                    Text(
                        "GIF",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteGif(url: String, modifier: Modifier = Modifier) {
    val drawable by produceState<Drawable?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 12_000
                    connection.readTimeout = 20_000
                    connection.instanceFollowRedirects = true
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                    ImageDecoder.decodeDrawable(source)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val image = drawable
        if (image == null) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(image)
                        (image as? AnimatedImageDrawable)?.start()
                    }
                },
                update = { view ->
                    if (view.drawable !== image) view.setImageDrawable(image)
                    (view.drawable as? AnimatedImageDrawable)?.start()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
