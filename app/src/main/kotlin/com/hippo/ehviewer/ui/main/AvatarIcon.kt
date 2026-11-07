package com.hippo.ehviewer.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.Option
import arrow.core.none
import arrow.core.some
import coil3.compose.AsyncImage
import coil3.network.HttpException
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.RollingNumber
import com.ehviewer.core.ui.component.RollingNumberPlaceholder
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.parser.HomeParser
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.login.refreshAccountInfo
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.util.displayString
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.from
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.toColorScheme
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import moe.tarsin.coroutines.runSwallowingWithUI

private val limitScope = CoroutineScope(Dispatchers.IO)
private val refreshEvent = MutableSharedFlow<Unit>()
private val invalidateEvent = MutableSharedFlow<Unit>()

typealias Result = Option<Either<String, HomeParser.Result>>

private val limitFlow: StateFlow<Result> = refreshEvent.conflate()
    .onEach {
        // If cached value is error, drop it when refresh
        val isError = limitFlow.value.isSome { it.isLeft() }
        if (isError) invalidateEvent.emit(Unit)
    }
    .map { catch { EhEngine.getImageLimits() }.mapLeft { e -> e.displayString() }.some() }
    .let { src -> merge(src, invalidateEvent.map { none() }) }
    .stateIn(limitScope, SharingStarted.Eagerly, none())

@Composable
context(_: CoroutineScope, _: DialogState, _: SnackbarHostState, _: DestinationsNavigator)
fun AvatarIcon() {
    val hasSignedIn by Settings.hasSignedIn.collectAsState()
    if (hasSignedIn) {
        val placeholder = stringResource(id = R.string.please_wait)
        val result by limitFlow.collectAsState()
        IconButton(
            onClick = {
                launch {
                    refreshEvent.emit(Unit)
                    awaitConfirmationOrCancel(
                        title = R.string.image_limits,
                        showConfirmButton = false,
                        showCancelButton = false,
                    ) {
                        val animatedAlpha by animateFloatAsState(if (remember { result } == result) 0.5f else 1f)
                        result.onNone {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularWavyProgressIndicator()
                                Spacer(modifier = Modifier.size(dimensionResource(id = com.hippo.ehviewer.R.dimen.keyline_margin)))
                                Text(text = placeholder)
                            }
                        }
                        result.onSome { current ->
                            Box(modifier = Modifier.graphicsLayer { alpha = animatedAlpha }) {
                                when (current) {
                                    is Either.Left -> Text(text = current.value)
                                    is Either.Right -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val (limits, funds) = current.value
                                        if (limits.maximum > 0) {
                                            val ratio = limits.current.toDouble() / limits.maximum
                                            val scheme = MaterialTheme.colorScheme
                                            val (warning, critical) = remember(scheme) { quotaAlertColors(scheme) }
                                            val color = when {
                                                ratio >= 1.0 -> critical
                                                ratio > 0.75 -> warning
                                                else -> scheme.primary
                                            }
                                            val value by animateFloatAsState(ratio.toFloat().coerceIn(0f, 1f))
                                            LinearWavyProgressIndicator(
                                                progress = { value.coerceIn(0f, 1f) },
                                                color = color,
                                                modifier = Modifier.height(12.dp).fillMaxWidth(),
                                            )
                                        }
                                        when (limits.maximum) {
                                            -1 -> Text(text = stringResource(id = R.string.image_limits_restricted))
                                            0 -> Text(text = stringResource(id = R.string.image_limits_normal))
                                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = stringResource(id = R.string.image_limits_summary))
                                                RollingNumber(number = limits.current)
                                                Text(text = " / ")
                                                RollingNumberPlaceholder(number = limits.maximum)
                                            }
                                        }
                                        Text(text = stringResource(id = R.string.current_funds))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                        ) {
                                            FundsItem(
                                                type = "GP",
                                                amount = funds.gp,
                                            )
                                            FundsItem(
                                                type = "C",
                                                amount = funds.credit,
                                            )
                                        }
                                        if (limits.resetCost > 0) {
                                            Button(
                                                onClick = {
                                                    launchIO {
                                                        runSwallowingWithUI {
                                                            invalidateEvent.emit(Unit)
                                                            EhEngine.resetImageLimits()
                                                            refreshEvent.emit(Unit)
                                                        }
                                                    }
                                                },
                                                shapes = ButtonDefaults.shapes(),
                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RestartAlt,
                                                    contentDescription = stringResource(id = R.string.reset),
                                                )
                                                Text(text = stringResource(id = R.string.reset_cost, limits.resetCost))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            val avatar by Settings.avatar.collectAsState()
            AnimatedContent(targetState = avatar == null) { noAvatar ->
                if (noAvatar) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null)
                } else {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        onError = { (_, r) ->
                            val e = r.throwable
                            if (e is HttpException && e.response.code == 404) {
                                launchIO { refreshAccountInfo() }
                            }
                        },
                        modifier = Modifier.clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    } else {
        IconButton(
            onClick = {
                launch {
                    awaitConfirmationOrCancel(
                        confirmText = R.string.sign_in,
                        showCancelButton = false,
                        text = { Text(text = stringResource(id = R.string.settings_eh_identity_cookies_guest)) },
                    )
                    EhUtils.signOut()
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(imageVector = Icons.Default.NoAccounts, contentDescription = null)
        }
    }
}

/** Low-chroma Tonal Spot roles, harmonized to Monet while retaining amber/red semantics. */
internal fun quotaAlertColors(scheme: ColorScheme): Pair<Color, Color> {
    val dark = scheme.surface.luminance() < 0.5f
    val theme = Hct.from(scheme.primary)
    fun tone(hue: Double) = SchemeTonalSpot(
        Blend.harmonize(Hct.from(hue, 36.0, 50.0), theme),
        dark,
        -0.1,
    ).toColorScheme().primary
    return tone(90.0) to tone(25.0)
}
