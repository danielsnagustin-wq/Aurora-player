package com.aurora.player.online

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inicio de sesión de YouTube Music con la cuenta de Google que ya
 * está en el teléfono (2026-11-10, pedido del usuario): el selector
 * de cuentas del sistema, sin escribir correo ni contraseña. El
 * WebView no puede ver esa cuenta; esta vía usa Google Sign-In y un
 * token OAuth que InnerTube acepta como Bearer. El token solo se
 * guarda en el teléfono (YouTubeMusicStore), igual que las cookies,
 * y nunca sale del dispositivo. Sin token ni cookies, la búsqueda
 * sigue anónima, como hoy.
 */
object GoogleAccountAuth {
    /** Alcance mínimo: leer su YouTube / YouTube Music. */
    const val YOUTUBE_SCOPE = "oauth2:https://www.googleapis.com/auth/youtube"

    fun signInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /** Cabecera Authorization para InnerTube (pura, con test). */
    fun buildBearerHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer ${token.trim()}")

    sealed interface TokenResult {
        data class Ok(val token: String) : TokenResult
        data class NeedsConsent(val intent: Intent) : TokenResult
        data object Failed : TokenResult
    }

    /**
     * Pide el token OAuth de la cuenta. La primera vez Google puede
     * pedir consentimiento (NeedsConsent): hay que lanzar ese intent
     * y volver a pedir el token al volver. Un token vacío o un error
     * de Google es Failed, nunca una excepción hacia arriba.
     */
    suspend fun fetchToken(context: Context, account: GoogleSignInAccount): TokenResult =
        withContext(Dispatchers.IO) {
            val googleAccount = account.account ?: return@withContext TokenResult.Failed
            try {
                val token = GoogleAuthUtil.getToken(context, googleAccount, YOUTUBE_SCOPE)
                if (token.isNullOrBlank()) TokenResult.Failed else TokenResult.Ok(token)
            } catch (e: UserRecoverableAuthException) {
                e.intent?.let { TokenResult.NeedsConsent(it) } ?: TokenResult.Failed
            } catch (_: GoogleAuthException) {
                TokenResult.Failed
            } catch (_: Exception) {
                TokenResult.Failed
            }
        }
}
