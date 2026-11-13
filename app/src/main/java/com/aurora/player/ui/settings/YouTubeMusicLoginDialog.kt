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
 * Inicio de sesión con YouTube Music dentro de la app (2026-11-10,
 * como BitChord): el usuario inicia sesión con su cuenta de Google
 * en music.youtube.com y Aurora lee las cookies de la sesión sola.
 * Las cookies solo se guardan en el teléfono (YouTubeMusicStore);
 * con ellas, la búsqueda de YouTube pasa a ser la de SU cuenta.
 * Sin sesión, la búsqueda sigue anónima, como hoy.
 */
@Composable
fun YouTubeMusicLoginDialog(
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var triedCookies by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    // RONDA ARREGLOS 2026-11-13: pegado manual de la sesión (la
    // ruta fiable cuando Google bloquea el WebView) + abrir la
    // página en Chrome, donde la cuenta ya está puesta.
    var manual by remember { mutableStateOf(false) }
    var manualCookies by remember { mutableStateOf("") }
    val appContext = LocalContext.current

    // Ronda 20: igual que el diálogo de Spotify — si la página no
    // termina de cargar (redirecciones que nunca disparan
    // onPageFinished), el spinner tapaba todo para siempre. A los
    // 12 s se quita igual: la página que haya cargado queda usable
    // y el usuario puede reintentar.
    // RONDA LOGIN (Codex #6): si a los 12 s no terminó, no dejarla
    // muda: avisar, porque una página en blanco sin mensaje era
    // justo lo que se veía en el teléfono.
    LaunchedEffect(loading) {
        if (loading) {
            delay(12_000)
            loading = false
            hint = "La página tardó demasiado en cargar. Si se ve en blanco, ciérrala y vuelve a intentarlo."
        }
    }

    fun readCookies(): String? {
        val cookies = CookieManager.getInstance()
            .getCookie("https://music.youtube.com") ?: return null
        return cookies.takeIf {
            YouTubeMusicAuth.extractCookie(it, "SAPISID") != null
        }
    }

    fun tryConnect() {
        val cookies = readCookies()
        if (cookies == null) {
            hint = "Todavía no veo tu sesión. Termina de iniciar sesión arriba y pulsa de nuevo."
            return
        }
        if (cookies == triedCookies) {
            hint = "Esa sesión ya se probó y no funcionó. Cierra sesión en la web y vuelve a entrar."
            return
        }
        triedCookies = cookies
        hint = "Comprobando la sesión…"
        onConnect(cookies)
    }

    // RONDA LOGIN 2026-11-11: music.youtube.com es una SPA y la
    // redirección final del login no siempre dispara onPageFinished.
    // Se revisan las cookies cada 2 s mientras el diálogo está
    // abierto: en cuanto aparece SAPISID, la sesión se guarda sola.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val cookies = readCookies()
            if (cookies != null && cookies != triedCookies) {
                triedCookies = cookies
                onConnect(cookies)
                break
            }
        }
    }

    // RONDA LOGIN 2026-11-11 (como BitChord, captura del usuario):
    // a PANTALLA COMPLETA, con cabecera propia ("Usar este perfil"
    // arriba a la derecha) y la web ocupando todo lo demás. El diálogo
    // pequeño (94% x 88%) recortaba la página y el WebView se veía
    // negro y feo. El UA de escritorio se conserva: con el UA móvil
    // Google rechaza el inicio ("navegador no seguro").
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
                        "Iniciar sesión en YouTube Music",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Entra con tu cuenta de Google. Cuando tu avatar aparezca arriba, pulsa «Usar este perfil».",
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
                            // RONDA LOGIN: la página a pantalla completa
                            // y ajustada al ancho (antes, sin viewport,
                            // la web de escritorio se veía recortada).
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
                            // RONDA LOGIN 2026-11-11: Google pone sus
                            // cookies de sesión desde otro dominio al
                            // iniciar sesión; sin esto pueden no quedarse.
                            CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            // Agente de Chrome de escritorio (2026-11-10):
                            // con el UA de WebView móvil Google rechaza
                            // el inicio ("navegador no seguro") y la web
                            // empuja su app. Con este, el login de Google
                            // funciona como en un navegador normal.
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
                                        hint = "No se pudo cargar YouTube Music. Revisa la conexión y pulsa Reintentar."
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
                                        hint = "YouTube Music devolvió un error (${errorResponse?.statusCode}). Pulsa Reintentar."
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    loadFailed = false
                                    // Al terminar de cargar cada página se
                                    // mira si la sesión ya está puesta.
                                    val cookies = readCookies()
                                    if (cookies != null && cookies != triedCookies) {
                                        triedCookies = cookies
                                        onConnect(cookies)
                                    }
                                }
                            }
                            // RONDA ARREGLOS 2026-11-13: el login de
                            // Google puede abrir popup (sin onCreateWindow
                            // el toque no hacía nada) y los errores JS de
                            // la página quedaban invisibles. El progreso
                            // real quita el spinner aunque la SPA no
                            // dispare onPageFinished.
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
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d(
                                        "YtMusicLogin",
                                        "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})",
                                    )
                                    return true
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (newProgress >= 100) loading = false
                                }
                            }
                            loadUrl("https://music.youtube.com")
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
                            "Cargando YouTube Music…",
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
                        Text("No se pudo cargar la página de YouTube Music.")
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
            // RONDA ARREGLOS 2026-11-13: Google bloquea el inicio en
            // WebView ("navegador no seguro") por política; la ruta
            // fiable es pegar la sesión que ya está puesta en Chrome.
            // Android no deja leer las cookies de otra app, así que el
            // pegado es el puente (igual que hace BitChord en TV).
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { manual = !manual },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Pegar las cookies manualmente")
                }
                TextButton(
                    onClick = {
                        try {
                            appContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")),
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
                    "Inicia sesión en Chrome (ahí ya está tu cuenta) y pega aquí el header de cookies. Queda solo en tu teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = manualCookies,
                    onValueChange = { manualCookies = it },
                    label = { Text("Cookies de music.youtube.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        // Se guarda el HEADER COMPLETO (buildAuthHeaders
                        // lo necesita entero); aquí solo se comprueba
                        // que trae SAPISID, sin el cual no hay sesión.
                        val header = manualCookies.trim()
                            .removePrefix("Cookie:").removePrefix("cookie:").trim()
                        if (YouTubeMusicAuth.parseManualSession(header, "SAPISID") == null) {
                            hint = "Falta SAPISID en lo pegado. Copia el header completo de cookies."
                            return@Button
                        }
                        triedCookies = header
                        onConnect(header)
                    },
                    colors = auroraButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = manualCookies.isNotBlank(),
                ) {
                    Text("Conectar YouTube Music")
                }
            }
        }
    }
}
