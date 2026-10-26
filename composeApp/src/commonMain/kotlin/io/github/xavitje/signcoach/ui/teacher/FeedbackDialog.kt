package io.github.xavitje.signcoach.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.remote.FeedbackApi
import io.github.xavitje.signcoach.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Feedbackformulier voor docenten tijdens de pilot: 5 korte vragen, 2 open vragen en toestemming
 * om het antwoord als quote te gebruiken. Kost ongeveer 2 minuten.
 */
@Composable
fun FeedbackDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (FeedbackApi.Answers) -> Unit) {
    var usage by remember { mutableStateOf<String?>(null) }
    var enjoy by remember { mutableStateOf<Int?>(null) }
    var recognition by remember { mutableStateOf<Int?>(null) }
    var ease by remember { mutableStateOf<Int?>(null) }
    var recommend by remember { mutableStateOf<Int?>(null) }
    var improve by remember { mutableStateOf("") }
    var best by remember { mutableStateOf("") }
    var mayQuote by remember { mutableStateOf(false) }
    val complete = usage != null && enjoy != null && recognition != null && ease != null && recommend != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feedback_title)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(Res.string.feedback_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)

                Question(Res.string.feedback_q_usage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "niet" to Res.string.feedback_usage_none,
                            "soms" to Res.string.feedback_usage_some,
                            "wekelijks" to Res.string.feedback_usage_weekly,
                            "vaker" to Res.string.feedback_usage_more,
                        ).forEach { (value, label) ->
                            FilterChip(selected = usage == value, onClick = { usage = value }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                Question(Res.string.feedback_q_enjoy) { Scale(1..5, enjoy) { enjoy = it } }
                Question(Res.string.feedback_q_recognition) { Scale(1..5, recognition) { recognition = it } }
                Question(Res.string.feedback_q_ease) { Scale(1..5, ease) { ease = it } }
                Question(Res.string.feedback_q_recommend) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Scale(0..5, recommend) { recommend = it }
                        Scale(6..10, recommend) { recommend = it }
                        Text(stringResource(Res.string.feedback_q_scale_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = best, onValueChange = { best = it.take(2000) },
                    label = { Text(stringResource(Res.string.feedback_q_best)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                OutlinedTextField(
                    value = improve, onValueChange = { improve = it.take(2000) },
                    label = { Text(stringResource(Res.string.feedback_q_improve)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mayQuote, onCheckedChange = { mayQuote = it })
                    Text(stringResource(Res.string.feedback_may_quote), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = complete && !busy,
                onClick = {
                    onSubmit(
                        FeedbackApi.Answers(
                            usage = usage!!, pupilsEnjoy = enjoy!!, recognition = recognition!!, easeOfUse = ease!!,
                            recommend = recommend!!, improve = improve, best = best, mayQuote = mayQuote,
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.feedback_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feedback_later)) } },
    )
}

@Composable
private fun Question(label: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun Scale(range: IntRange, selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        range.forEach { n ->
            FilterChip(
                selected = selected == n,
                onClick = { onSelect(n) },
                label = { Text(n.toString(), textAlign = TextAlign.Center, modifier = Modifier.size(width = 18.dp, height = 20.dp)) },
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}
