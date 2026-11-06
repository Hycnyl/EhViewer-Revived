package com.hippo.ehviewer.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
    val currentFunds = stringResource(R.string.current_funds)
    val archiveFree = stringResource(R.string.archive_free)
    val archiveOriginal = stringResource(R.string.archive_original)
    val archiveResample = stringResource(R.string.archive_resample)
    val fundsStyle = MaterialTheme.typography.labelLarge
    val minimumFundsWidth = 112.dp * LocalDensity.current.fontScale
    val (hath, nonHath) = remember(items) { items.partition { it.isHath } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = currentFunds, style = MaterialTheme.typography.titleSmall)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val spacing = 8.dp
                        val fundsWidth = if (maxWidth >= minimumFundsWidth * 2 + spacing) (maxWidth - spacing) / 2 else maxWidth
                        // RollingNumber contains a LazyRow: use finite widths, not intrinsic measurements.
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
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.archive_download_options),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(nonHath) {
            ArchiveItem(
                name = if (it.res == "org") archiveOriginal else archiveResample,
                cost = if (it.cost == "Free!") archiveFree else it.cost,
                size = it.size,
                onClick = { onItemClick(it) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "H@H")
            }
        }
        items(hath) {
            ArchiveItem(
                name = if (it.res == "org") archiveOriginal else it.name,
                cost = if (it.cost == "Free") archiveFree else it.cost,
                size = it.size,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelStyle = MaterialTheme.typography.labelMedium
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = cost,
            style = labelStyle,
        )
        Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().widthIn(min = 100.dp)) {
            Text(text = name)
        }
        Text(
            text = size,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
