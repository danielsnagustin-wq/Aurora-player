package com.aurora.player.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
    // RONDA CAZA 2026-11-13 (P1): el popup de login se creaba en
    // onCreateWindow pero nunca se mostraba ni se destruía: se abría
    // invisible, sin poder tocarlo ni cerrarlo.
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    // RONDA ARREGLOS 2026-11-13: pegado manual del token (la ruta
    // fiable si la web no deja leer la sesión) + abrir en Chrome.
    var manual by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    val appContext = LocalContext.current

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
        // RONDA ARREGLOS 2026-11-13: la cookie puede vivir en otro
        // host que soundcloud.com (api/secure/m): se prueban todos.
        val manager = CookieManager.getInstance()
        for (host in listOf(
            "https://soundcloud.com",
            "https://api.soundcloud.com",
            "https://secure.soundcloud.com",
            "https://m.soundcloud.com",
        )) {
            val cookies = manager.getCookie(host) ?: continue
            YouTubeMusicAuth.extractCookie(cookies, "oauth_token")?.let { return it }
        }
        return null
    }

    // RONDA ARREGLOS 2026-11-13: la web actual de SoundCloud puede
    // guardar la sesión en localStorage en vez de en una cookie. Se
    // sondea en cada vuelta del bucle; el resultado llega por
    // callback (la lectura JS es asíncrona).
    fun readTokenFromStorage() {
        val wv = webView ?: return
        wv.evaluateJavascript(
            "(function(){var t=localStorage.getItem('oauth_token')||localStorage.getItem('sc-token');return t||'';})()",
        ) { result ->
            val token = result?.trim()?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "null" } ?: return@evaluateJavascript
            if (token != triedToken) {
                triedToken = token
                onConnect(token)
            }
        }
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
            readTokenFromStorage()
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
                            // RONDA ARREGLOS 2026-11-13: fondo NEGRO
                            // opaco (el TRANSPARENT sobre AMOLED se
                            // veía negro antes del primer frame) y
                            // ventanas múltiples para el login por popup.
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.setSupportMultipleWindows(true)
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
                            // RONDA ARREGLOS 2026-11-13: el login puede
                            // abrir popup (sin onCreateWindow el toque no
                            // hacía nada) y los errores JS quedaban
                            // invisibles. El progreso real quita el
                            // spinner aunque la SPA no dispare
                            // onPageFinished.
                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?,
                                ): Boolean {
                                    val newWebView = WebView(view!!.context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.userAgentString = view.settings.userAgentString
                                        webViewClient = view.webViewClient
                                    }
                                    popupWebView = newWebView
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(view: WebView?) {
                                    // La página cerró el popup sola: se
                                    // quita el diálogo; el onRelease del
                                    // AndroidView destruye el WebView.
                                    popupWebView = null
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d(
                                        "SoundCloudLogin",
                                        "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})",
                                    )
                                    return true
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (newProgress >= 100) loading = false
                                }
                            }
                            loadUrl("https://soundcloud.com")
                        }
                    },
                    // RONDA 2 (2026-11-13, P1, n=915): el WebView
                    // principal se FILTRABA: solo el popup tenía
                    // onRelease (n=850–852). Sin onRelease, cada
                    // login deja un WebView vivo (hilo de render)
                    // hasta que el proceso muere.
                    onRelease = { it.stopLoading(); it.destroy(); webView = null },
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
            // RONDA ARREGLOS 2026-11-13: si la web no deja leer la
            // sesión, el pegado manual del token es la ruta fiable.
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { manual = !manual },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Pegar el token manualmente")
                }
                TextButton(
                    onClick = {
                        try {
                            appContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://soundcloud.com")),
                            )
                        } catch (_: Exception) {
                            hint = "No se pudo abrir el navegador."
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Abrir en Chrome")
                }
            }
            if (manual) {
                Text(
                    "Inicia sesión en Chrome y pega el token oauth_token (el valor o el header completo). Queda solo en tu teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Token oauth_token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        val token = YouTubeMusicAuth.parseManualSession(manualToken, "oauth_token")
                        if (token == null) {
                            hint = "Falta oauth_token en lo pegado."
                            return@Button
                        }
                        triedToken = token
                        onConnect(token)
                    },
                    colors = auroraButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = manualToken.isNotBlank(),
                ) {
                    Text("Conectar SoundCloud")
                }
            }
        }
    }

    // RONDA CAZA 2026-11-13 (P1): el popup va en su propio diálogo
    // encima, con botón de cierre. El onRelease destruye el WebView
    // al salir de la composición (después de desanclarlo, nunca
    // antes); por eso el cierre solo limpia el estado.
    popupWebView?.let { popup ->
        Dialog(
            onDismissRequest = { popupWebView = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ventana emergente",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { popupWebView = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                AndroidView(
                    factory = { popup },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                )
            }
        }
    }
}
