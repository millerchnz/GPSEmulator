package com.millerchnz.gpsemulator.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

object ExternalLinks {
    val privacyPolicy: Uri = Uri.parse("https://chaosferret.net/gpsdebugtool_privacy.html")
    val playStore: Uri = Uri.parse("https://play.google.com/apps/testing/com.millerchnz.gpsemulator")
}

fun Context.openExternalLink(uri: Uri) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/** Opens the published privacy policy. Play requires this to be reachable inside the app. */
@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(
        onClick = { context.openExternalLink(ExternalLinks.privacyPolicy) },
        modifier = modifier,
    ) {
        Text("Privacy policy")
    }
}
