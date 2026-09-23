package io.github.xavitje.signcoach.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.remote.BadgeApi

/**
 * Fase D — badge-overzicht: alle mogelijke badges op een rij, behaalde badges in kleur, nog
 * niet behaalde badges grijs met een sluiting-hint. Lege staat (nog geen enkele badge behaald)
 * blijft gewoon de volledige (grijze) rij tonen — badges zijn immers per definitie een "nog te
 * behalen" lijst, dus grijs-en-compleet is hier de juiste lege staat, niet een leeg scherm.
 */
@Composable
fun BadgesSection(
    badges: List<BadgeApi.Badge>,
    earnedBadgeIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            modifier = Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(badges, key = { it.id }) { badge ->
                val earned = earnedBadgeIds.contains(badge.id)
                Column(
                    modifier = Modifier.width(76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BadgeIcon(iconKey = badge.iconKey, locked = !earned, size = 64.dp)
                    Text(
                        text = badge.name,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
