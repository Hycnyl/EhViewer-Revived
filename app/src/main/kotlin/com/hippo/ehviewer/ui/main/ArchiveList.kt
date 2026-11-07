package com.hippo.ehviewer.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        items(nonHath) {
            ArchiveItem(
                name = if (it.res == "org") archiveOriginal else archiveResample,
                cost = if (it.cost == "Free!") archiveFree else it.cost,
                size = it.size,
                isHath = false,
                onClick = { onItemClick(it) },
            )
        }
        if (hath.isNotEmpty() && nonHath.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
        items(hath) {
            ArchiveItem(
                name = if (it.res == "org") archiveOriginal else it.name,
                cost = if (it.cost == "Free") archiveFree else it.cost,
                size = it.size,
                isHath = true,
                onClick = { onItemClick(it) },
            )
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
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (isHath) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Text("H@H", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(cost, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
