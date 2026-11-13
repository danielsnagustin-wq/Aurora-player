package com.aurora.player.ui.settings

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aurora.player.online.YouTubeMusicAuth
import com.aurora.player.ui.theme.auroraButtonColors
import kotlinx.coroutines.delay

/**
 * Inicio de sesión con SoundCloud dentro de la app (Descubrimiento,
 * 2026-11-13, como el de YouTube Music): el usuario inicia sesión en
 * soundcloud.com y Aurora lee el token oauth_token de la sesión sola.
 * El token solo se guarda en el teléfono (SoundCloudStore); con él se
 * leen /me y el feed de seguidos (/stream). Sin sesión, SoundCloud
 * sigue anónimo, como hoy.
 */
@Composable
fun SoundCloudLoginDialog(
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var triedToken by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // Como el diálogo de YouTube Music: si la página no termina de
    // cargar, el spinner se quita a los 12 s y se avisa.
    LaunchedEffect(loading) {
        if (loading) {
            delay(12_000)
            loading = false
            hint = "La página tardó demasiado en cargar. Si se ve en blanco, ciérrala y vuelve a intentarlo."
        }
    }

    fun readToken(): String? {
        val cookies = CookieManager.getInstance()
            .getCookie("https://soundcloud.com") ?: return null
        return YouTubeMusicAuth.extractCookie(cookies, "oauth_token")
    }

    fun tryConnect() {
        val token = readToken()
        if (token == null) {
            hint = "Todavía no veo tu sesión. Termina de iniciar sesión arriba y pulsa de nuevo."
            return
        }
        if (token == triedToken) {
            hint = "Esa sesión ya se probó y no funcionó. Cierra sesión en la web y vuelve a entrar."
            return
        }
        triedToken = token
        hint = "Comprobando la sesión…"
        onConnect(token)
    }

    // soundcloud.com es una SPA y la redirección final del login no
    // siempre dispara onPageFinished. Se revisa el token cada 2 s
    // mientras el diálogo está abierto: en cuanto aparece oauth_token,
    // la sesión se guarda sola.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val token = readToken()
            if (token != null && token != triedToken) {
                triedToken = token
                onConnect(token)
                break
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Iniciar sesión en SoundCloud",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Entra con tu cuenta de SoundCloud. Cuando veas tu inicio, pulsa «Usar este perfil».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { tryConnect() }) {
                    Text("Usar este perfil")
                }
            }
            hint?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            // Agente de Chrome de escritorio, como el
                            // diálogo de YouTube Music: la web de
                            // SoundCloud sirve sus JS solo así.
                            settings.userAgentString =
                                "Mozilla/5.0 (X11; Linux x86_64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/126.0.0.0 Safari/537.36"
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    loading = true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        loading = false
                                        loadFailed = true
                                        hint = "No se pudo cargar SoundCloud. Revisa la conexión y pulsa Reintentar."
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    if (request?.isForMainFrame == true &&
                                        (errorResponse?.statusCode ?: 200) !in 200..299
                                    ) {
                                        loading = false
                                        loadFailed = true
                                        hint = "SoundCloud devolvió un error (${errorResponse?.statusCode}). Pulsa Reintentar."
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    loadFailed = false
                                    val token = readToken()
                                    if (token != null && token != triedToken) {
                                        triedToken = token
                                        onConnect(token)
                                    }
                                }
                            }
                            loadUrl("https://soundcloud.com")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Cargando SoundCloud…",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (loadFailed) {
                    Column(
                        modifier = Modifier.align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No se pudo cargar la página de SoundCloud.")
                        Button(
                            onClick = {
                                loadFailed = false
                                loading = true
                                webView?.reload()
                            },
                            colors = auroraButtonColors(),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}
