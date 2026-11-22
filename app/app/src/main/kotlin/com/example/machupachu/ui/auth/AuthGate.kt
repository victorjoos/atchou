package com.example.machupachu.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.machupachu.MachuApp
import com.example.machupachu.R
import com.example.machupachu.data.LoginRejectedException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/** Shows the sign-in screen whenever the server asks for a login (no token yet, or the token expired). */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val machu = context.applicationContext as MachuApp
  val loginRequired by machu.loginRequired.collectAsStateWithLifecycle()

  // A server without auth never answers 401, so ask up front; ignore failures (the screens report them).
  LaunchedEffect(Unit) {
    runCatching { machu.api.authConfig() }.onSuccess { if (it.authRequired && machu.settings.sessionToken == null) machu.requireLogin() }
  }

  if (loginRequired) SignInScreen(machu) else content()
}

@Composable
private fun SignInScreen(machu: MachuApp) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun signIn() {
    scope.launch {
      busy = true
      error = null
      try {
        val clientId = machu.api.authConfig().googleClientId
        if (clientId == null) {
          error = context.getString(R.string.auth_not_configured)
          return@launch
        }
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        val credential = CredentialManager.create(context).getCredential(context as Activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          machu.loginDone(machu.api.loginGoogle(idToken).token)
        }
      } catch (e: LoginRejectedException) {
        error = context.getString(R.string.auth_not_allowed)
      } catch (e: Exception) {
        error = context.getString(R.string.auth_failed, e.message ?: e.javaClass.simpleName)
      } finally {
        busy = false
      }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.auth_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.auth_text))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = ::signIn, enabled = !busy) { Text(stringResource(R.string.auth_sign_in)) }
  }
}
