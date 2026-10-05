package com.millerchnz.gpsemulator.ui.about

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.millerchnz.gpsemulator.ui.common.ExternalLinks
import com.millerchnz.gpsemulator.ui.common.openExternalLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onShowHelp: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("About") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "GPS Debug Tool feeds a chosen position to other apps, records every value the " +
                    "Android location API reports, and watches geofences. It is meant for testing " +
                    "software that depends on GPS.",
                style = MaterialTheme.typography.bodyLarge,
            )
            PlayStoreQr(
                onClick = { context.openExternalLink(ExternalLinks.playStore) },
            )
            Button(
                onClick = onShowHelp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show help")
            }
            OutlinedButton(
                onClick = { context.openExternalLink(ExternalLinks.privacyPolicy) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Privacy Policy")
            }
        }
    }
}

@Composable
private fun PlayStoreQr(
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 220.dp.roundToPx() }
    val qr = remember(sizePx) { playStoreQr(ExternalLinks.playStore.toString(), sizePx) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = qr,
                contentDescription = "QR code for the GPS Debug Tool Play Store listing",
                modifier = Modifier.size(220.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "Play Store",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0B1F2A),
            )
        }
    }
}

/** Black modules on white, so the code still scans in dark theme. */
private fun playStoreQr(contents: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        contents,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bitmap.asImageBitmap()
}
