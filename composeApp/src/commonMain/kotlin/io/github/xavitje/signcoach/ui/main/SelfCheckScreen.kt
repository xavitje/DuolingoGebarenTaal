package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.common.CompletionCheckmark
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Cream
import io.github.xavitje.signcoach.ui.theme.Gold
import io.github.xavitje.signcoach.ui.theme.Ink
import io.github.xavitje.signcoach.ui.theme.InkSoft
import io.github.xavitje.signcoach.ui.theme.Plum
import io.github.xavitje.signcoach.ui.theme.PlumDeep
import org.jetbrains.compose.resources.stringResource

/**
 * Oefenen zonder camera-herkenning: voor gebaren die al een demovideo hebben maar (nog) geen
 * herkenningstemplate. De leerling doet het gebaar na en geeft zelf aan dat het lukte. Zo is
 * elke les met content te halen en blijft het leerpad nooit vastzitten op ontbrekende
 * herkenningsdata. Zodra er een template is, krijgt dezelfde les automatisch de camera-oefening.
 */
@Composable
fun SelfCheckScreen(
    gestureName: String,
    completed: Boolean,
    earnedXp: Int?,
    onDone: () -> Unit,
    onWatchAgain: () -> Unit,
    onClose: () -> Unit,
) {
    if (completed) {
        Column(
            modifier = Modifier.fillMaxSize().background(Cream).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompletionCheckmark()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.coach_success_title), style = MaterialTheme.typography.headlineMedium, color = Plum)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.coach_success_desc), style = MaterialTheme.typography.bodyLarge, color = InkSoft, textAlign = TextAlign.Center)
            earnedXp?.let { xp ->
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(50), color = Gold) {
                    Text(
                        stringResource(Res.string.coach_xp_earned, xp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
            ) { Text(stringResource(Res.string.coach_back_to_learn), fontWeight = FontWeight.Bold) }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PlumDeep).padding(horizontal = 24.dp),
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
                stringResource(Res.string.selfcheck_title),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.65f),
            )
            Text(
                gestureName,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.selfcheck_desc),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
            ) {
                Text(stringResource(Res.string.selfcheck_done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onWatchAgain,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(stringResource(Res.string.selfcheck_watch_again), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
