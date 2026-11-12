package com.aurora.player.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aurora.player.ui.theme.auroraButtonColors
import kotlinx.coroutines.delay

/**
 * Inicio de sesión con Spotify dentro de la app (como BitChord,
 * 2026-11-10): el usuario inicia sesión con su cuenta en el
 * WebView y Aurora lee la cookie sp_dc sola, sin que nadie tenga
 * que abrir las herramientas de desarrollador ni copiar nada a
 * mano. La cookie solo se guarda en el teléfono (SpotifyStore).
 * Si el WebView no la consigue, queda el campo manual de siempre.
 */
@Composable
fun SpotifyLoginDialog(
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var triedCookie by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualCookie by remember { mutableStateOf("") }

    // Si la página no termina de cargar (redirecciones de Spotify
    // que nunca disparan onPageFinished), el spinner tapaba el
    // formulario para siempre. A los 12 s se quita igual: la página
    // que haya cargado queda usable y el usuario puede reintentar.
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

    fun readCookie(): String? {
        val cookies = CookieManager.getInstance()
            .getCookie("https://open.spotify.com") ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.removePrefix("sp_dc=")
            ?.takeIf { it.isNotBlank() }
    }

    fun tryConnect() {
        val cookie = readCookie()
        if (cookie == null) {
            hint = "Todavía no veo tu sesión. Termina de iniciar sesión arriba y pulsa de nuevo."
            return
        }
        if (cookie == triedCookie) {
            hint = "Esa sesión ya se probó y no funcionó. Cierra sesión en la web y vuelve a entrar."
            return
        }
        triedCookie = cookie
        hint = "Comprobando la sesión…"
        onConnect(cookie)
    }

    // RONDA LOGIN 2026-11-11: el reproductor web es una SPA y la
    // navegación tras entrar no siempre dispara onPageFinished.
    // Se revisa la cookie cada 2 s mientras el diálogo está
    // abierto: en cuanto aparece sp_dc, la sesión se guarda sola.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val cookie = readCookie()
            if (cookie != null && cookie != triedCookie) {
                triedCookie = cookie
                onConnect(cookie)
                break
            }
        }
    }

    // RONDA LOGIN 2026-11-11 (como BitChord, captura del usuario):
    // a PANTALLA COMPLETA, con cabecera propia ("Usar este perfil"
    // arriba a la derecha) y la web ocupando todo lo demás. El diálogo
    // pequeño (94% x 88%) recortaba la página y el WebView se veía
    // negro y feo. El UA de escritorio se conserva: con el UA móvil
    // Spotify sirve otro flujo (o rechaza) y el formulario no aparece.
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
                        "Iniciar sesión con Spotify",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Entra con tu cuenta de Spotify. Cuando la sesión esté puesta, pulsa «Usar este perfil».",
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
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            CookieManager.getInstance().setAcceptCookie(true)
                            // RONDA LOGIN 2026-11-11: Spotify pone la
                            // cookie sp_dc desde otro dominio al iniciar
                            // sesión; sin esto puede no quedarse.
                            CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            // Agente de Chrome de escritorio (2026-11-10):
                            // igual que YouTube Music, Spotify sirve otro
                            // flujo (o rechaza) al WebView móvil y el
                            // formulario no aparece nunca.
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

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    // Spotify redirige a spotify:// para
                                    // abrir su app (2026-11-10: sin esto
                                    // el WebView moría con
                                    // ERR_UNKNOWN_URL_SCHEME). Los enlaces
                                    // que no son web se abren fuera y no
                                    // se cargan dentro.
                                    val uri = request?.url ?: return false
                                    if (uri.scheme == "http" || uri.scheme == "https") {
                                        return false
                                    }
                                    try {
                                        view?.context?.startActivity(
                                            Intent(Intent.ACTION_VIEW, uri),
                                        )
                                    } catch (_: ActivityNotFoundException) {
                                        hint = "Spotify quiere abrir su app, que no está instalada. Sigue iniciando sesión aquí."
                                    }
                                    return true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // Sin esto, una carga fallida se
                                    // quedaba en una caja negra muda.
                                    if (request?.isForMainFrame == true) {
                                        loading = false
                                        loadFailed = true
                                        hint = "No se pudo cargar Spotify. Revisa la conexión y pulsa Reintentar."
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
                                        hint = "Spotify devolvió un error (${errorResponse?.statusCode}). Pulsa Reintentar."
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    loadFailed = false
                                    // Al terminar de cargar cada página se
                                    // mira si la sesión ya está puesta.
                                    val cookie = readCookie()
                                    if (cookie != null && cookie != triedCookie) {
                                        triedCookie = cookie
                                        onConnect(cookie)
                                    }
                                }
                            }
                            // RONDA LOGIN 2026-11-11: entrar por el
                            // reproductor web (open.spotify.com), como
                            // hacen Spotube y MuSicX. La entrada vieja
                            // (accounts.spotify.com/login) se quedaba en
                            // negro en el WebView: cargaba sin error pero
                            // no pintaba nada. Con el agente de escritorio
                            // el reproductor web enseña su botón de entrar
                            // y la cookie sp_dc se pone igual.
                            loadUrl("https://open.spotify.com/")
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
                            "Cargando Spotify…",
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
                        Text("No se pudo cargar la página de Spotify.")
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
            TextButton(
                onClick = { manual = !manual },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pegar la cookie manualmente")
            }
            if (manual) {
                OutlinedTextField(
                    value = manualCookie,
                    onValueChange = { manualCookie = it },
                    label = { Text("Cookie sp_dc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onConnect(manualCookie) },
                    colors = auroraButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = manualCookie.isNotBlank(),
                ) {
                    Text("Conectar Spotify")
                }
            }
        }
    }
}
