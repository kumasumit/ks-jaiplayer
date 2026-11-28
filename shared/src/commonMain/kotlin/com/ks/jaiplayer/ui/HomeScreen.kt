package com.ks.jaiplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerError
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.app_name
import com.ks.jaiplayer.resources.common_settings
import com.ks.jaiplayer.resources.error_vlc_missing_body
import com.ks.jaiplayer.resources.error_vlc_missing_title
import com.ks.jaiplayer.resources.home_drop_hint
import com.ks.jaiplayer.resources.home_open_file
import com.ks.jaiplayer.resources.home_open_series
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(app: JaiPlayerState) {
    val m = LocalMetrics.current
    val form = LocalFormFactor.current
    val engineState by app.engine.state.collectAsState()
    val openFocus = remember { FocusRequester() }
    if (form == FormFactor.Tv) LaunchedEffect(Unit) { runCatching { openFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize()
            .mediaDropTarget { files -> app.openAll(files) }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(m.safe),
    ) {
        IconButton(onClick = { app.showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).tvFocusRing()) {
            Icon(JaiIcons.Settings, stringResource(Res.string.common_settings), Modifier.size(m.icon))
        }
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(dev = app.host.isDev)
            Spacer(Modifier.height(32.dp))
            if (engineState.error == PlayerError.EngineMissing) {
                EngineMissingCard()
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = app::pickAndOpen,
                modifier = Modifier.focusRequester(openFocus).tvFocusRing(RoundedCornerShape(14.dp)).height(m.button + 12.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(JaiIcons.Folder, null, Modifier.size(m.icon))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(Res.string.home_open_file), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = app::pickFolderAndOpen,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.tvFocusRing(RoundedCornerShape(14.dp)).height(m.button + 8.dp),
            ) {
                Icon(JaiIcons.Episodes, null, Modifier.size(m.icon))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(Res.string.home_open_series))
            }
            if (form == FormFactor.Desktop) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(Res.string.home_drop_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Sections added by plugins (recent files, …).
            app.plugins.homeSections.forEach { section ->
                Spacer(Modifier.height(32.dp))
                section()
            }
        }
    }
}

@Composable
private fun Wordmark(dev: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp)) {
            Icon(
                JaiIcons.Play, null,
                Modifier.padding(8.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.app_name) + if (dev) " DEV" else "", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EngineMissingCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.error_vlc_missing_title),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.error_vlc_missing_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
