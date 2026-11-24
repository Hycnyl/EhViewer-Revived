// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ehviewer.core.i18n.R
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhApplication.Companion.imageCache
import com.hippo.ehviewer.coil.ReadCacheCleaner
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectDate
import com.hippo.ehviewer.ui.tools.awaitSelectItemWithIcon
import com.hippo.ehviewer.ui.tools.awaitSelectTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar

@Composable
context(ctx: Context, _: DialogState, _: SnackbarHostState)
internal fun ReadCacheCleanupPreference(openGalleries: () -> Unit) {
    var active by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.read_cache_cleanup)
    Preference(
        title = title,
        summary = stringResource(if (clearing) R.string.read_cache_cleanup_running else R.string.read_cache_cleanup_summary),
    ) {
        if (active) return@Preference
        active = true
        scope.launch {
            try {
                val choices = listOf(
                    Icons.Outlined.Schedule to R.string.read_cache_cleanup_by_time,
                    Icons.Outlined.Collections to R.string.read_cache_cleanup_by_gallery,
                )
                when (awaitSelectItemWithIcon(choices, title)) {
                    0 -> {
                        val now = ZonedDateTime.now()
                        val date = awaitSelectDate(
                            title = R.string.read_cache_cleanup_date,
                            initialSelectedDateMillis = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        )
                        val (hour, minute) = awaitSelectTime(ctx.getString(R.string.read_cache_cleanup_time), now.hour, now.minute)
                        val cutoff = readCacheCutoffMillis(date, hour, minute, now.zone)
                        val label = formatReadCacheTime(ctx, cutoff)
                        awaitConfirmationOrCancel(confirmText = R.string.read_cache_cleanup_action, title = R.string.read_cache_cleanup_by_time) {
                            Text(stringResource(R.string.read_cache_cleanup_confirm_time, label))
                        }
                        clearing = true
                        clearReadingCache(scope) { ReadCacheCleaner.clearBefore(imageCache, cutoff) }
                    }
                    1 -> openGalleries()
                }
            } finally {
                clearing = false
                active = false
            }
        }
    }
}

internal fun readCacheCutoffMillis(utcDate: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    Instant.ofEpochMilli(utcDate).atOffset(ZoneOffset.UTC).toLocalDate()
        .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

internal fun formatReadCacheTime(context: Context, millis: Long): String {
    val date = Date(millis)
    return DateFormat.getMediumDateFormat(context).format(date) + " " + DateFormat.getTimeFormat(context).format(date)
}

context(ctx: Context, _: SnackbarHostState)
internal suspend fun clearReadingCache(scope: CoroutineScope, action: () -> Int) {
    val message = runSuspendCatching { runInterruptible(Dispatchers.IO, action) }.fold(
        onSuccess = { ctx.getString(R.string.read_cache_cleanup_done, it) },
        onFailure = {
            logcat(it)
            ctx.getString(R.string.read_cache_cleanup_failed)
        },
    )
    scope.launch { snackbar(message) }
}
