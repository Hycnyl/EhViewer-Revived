package com.hippo.ehviewer.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.hippo.ehviewer.client.parser.Archive
import com.hippo.ehviewer.client.parser.Funds

@Composable
fun ArchiveList(
    funds: Funds,
    items: List<Archive>,
    onItemClick: (Archive) -> Unit,
    modifier: Modifier = Modifier,
) {
    val archiveFree = stringResource(R.string.archive_free)
    val archiveOriginal = stringResource(R.string.archive_original)
    val archiveResample = stringResource(R.string.archive_resample)
    val fundsStyle = MaterialTheme.typography.labelLarge
    val minimumFundsWidth = 112.dp * LocalDensity.current.fontScale
    val (hath, nonHath) = remember(items) { items.partition { it.isHath } }
    @Composable
    fun Options(options: List<Archive>, isHath: Boolean) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 8.dp) / 2
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                options.forEach { archive ->
                    ArchiveItem(
                        name = if (archive.res == "org") {
                            archiveOriginal
                        } else if (isHath) {
                            archive.name
                        } else {
                            archiveResample
                        },
                        cost = if (archive.cost == if (isHath) "Free" else "Free!") archiveFree else archive.cost,
                        size = archive.size,
                        isHath = isHath,
                        onClick = { onItemClick(archive) },
                        modifier = Modifier.width(width),
                    )
                }
            }
        }
    }
    // Only a few download choices: wrap their height instead of reserving a full lazy viewport.
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val spacing = 8.dp
            val fundsWidth = if (maxWidth >= minimumFundsWidth * 2 + spacing) (maxWidth - spacing) / 2 else maxWidth
            // RollingNumber contains a LazyRow: keep finite widths and allow large text to wrap.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                itemVerticalAlignment = Alignment.CenterVertically,
                maxItemsInEachRow = 2,
            ) {
                FundsItem("GP", funds.gp, Modifier.width(fundsWidth), fundsStyle)
                FundsItem("C", funds.credit, Modifier.width(fundsWidth), fundsStyle)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (nonHath.isNotEmpty()) Options(nonHath, false)
        if (hath.isNotEmpty()) {
            if (nonHath.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Options(hath, true)
        }
    }
}

@Composable
private fun ArchiveItem(
    name: String,
    cost: String,
    size: String,
    isHath: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(cost, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (isHath) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Text("H@H", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
