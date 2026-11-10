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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Iniciar sesión con YouTube Music",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
            Text(
                "Entra con tu cuenta de Google. Aurora solo guarda la sesión en tu teléfono, no sube nada.",
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { tryConnect() },
                colors = auroraButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ya inicié sesión")
            }
            hint?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cerrar")
            }
        }
    }
}
