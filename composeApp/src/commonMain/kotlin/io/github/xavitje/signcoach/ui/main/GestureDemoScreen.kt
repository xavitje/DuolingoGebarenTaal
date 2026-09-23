package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.platform.PlatformVideoPlayer
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Plum
import io.github.xavitje.signcoach.ui.theme.PlumDeep
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

/**
 * "Bekijk eerst"-scherm: getoond vóórdat de gebruiker een gebaar zelf voor de camera probeert.
 * Toont een referentievideo (bron: Corpus NGT/Global Signbank — zie PLAN.md §1/§3) van een
 * correct uitgevoerd gebaar, zodat de gebruiker weet wat 'm te doen staat voordat de camera
 * aangaat — in plaats van te moeten raden en per ongeluk een fout gebaar in te oefenen.
 *
 * [demoVideoUrl] is meestal nog null (de meeste van de 36 gebaren hebben nog geen gekoppelde
 * referentievideo) — in dat geval tonen we een nette "volgt nog"-staat in plaats van niets of
 * een crash, en kan de gebruiker gewoon direct oefenen.
 */
@Composable
fun GestureDemoScreen(
    gestureName: String,
    demoVideoUrl: String?,
    onStartPractice: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PlumDeep)
            .padding(horizontal = 24.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.contrib_back), tint = Color.White)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(Res.string.demo_watch_first),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.65f),
            )
            Text(
                gestureName,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Box(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Plum,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            ) {
                // Zero Trust: neem nooit aan dat het afspelen lukt — een mislukte video (kapotte
                // URL, geen netwerk, onbekend formaat) moet dezelfde nette fallback tonen als
                // "nog geen video", nooit een blijvend zwart vlak.
                var videoFailed by remember(demoVideoUrl) { mutableStateOf(false) }

                if (demoVideoUrl != null && !videoFailed) {
                    PlatformVideoPlayer(
                        url = demoVideoUrl,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                        onError = { videoFailed = true },
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Box(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(
                                if (videoFailed) Res.string.demo_video_error else Res.string.demo_coming_soon
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Box(modifier = Modifier.height(32.dp))

            Button(
                onClick = onStartPractice,
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    stringResource(Res.string.demo_start_practice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
