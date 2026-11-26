package com.hippo.ehviewer.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
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
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.RemoveContact
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor

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

        @Composable
        fun AvatarContent() {
            val avatar by Settings.avatar.collectAsState()
            AnimatedContent(targetState = avatar == null) { noAvatar ->
                if (noAvatar) {
                    Icon(imageVector = MiuixIcons.Contacts, contentDescription = null)
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

        val result by limitFlow.collectAsState()
        // 就近悬浮气泡：点击头像后直接在按钮正下方弹出图片配额与资金信息，不再从屏幕底部弹出大面板
        val menuEntry = DropdownEntry(
            items = buildList {
                result.fold(
                    ifEmpty = {
                        add(DropdownItem(text = placeholder, enabled = false))
                    },
                    ifSome = { current ->
                        when (current) {
                            is Either.Left -> add(DropdownItem(text = current.value, enabled = false))
                            is Either.Right -> {
                                val (limits, funds) = current.value
                                val limitSummary = when (limits.maximum) {
                                    -1 -> stringResource(id = R.string.image_limits_restricted)
                                    0 -> stringResource(id = R.string.image_limits_normal)
                                    else -> "${limits.current} / ${limits.maximum}"
                                }
                                add(
                                    DropdownItem(
                                        text = stringResource(id = R.string.image_limits),
                                        summary = limitSummary,
                                        enabled = false,
                                    ),
                                )
                                add(DropdownItem(text = "GP", summary = funds.gp.toString(), enabled = false))
                                add(DropdownItem(text = "Credits", summary = funds.credit.toString(), enabled = false))
                                if (limits.resetCost > 0) {
                                    add(
                                        DropdownItem(
                                            text = stringResource(id = R.string.reset_cost, limits.resetCost),
                                            onClick = {
                                                launchIO {
                                                    runSwallowingWithUI {
                                                        invalidateEvent.emit(Unit)
                                                        EhEngine.resetImageLimits()
                                                        refreshEvent.emit(Unit)
                                                    }
                                                }
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
            },
        )
        val menuAnchor = rememberGlassPopupAnchor()
        var menuShow by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = {
                    menuShow = true
                    launch { refreshEvent.emit(Unit) }
                },
                modifier = Modifier.glassPopupAnchor(menuAnchor, cornerRadius = 20.dp),
            ) {
                AvatarContent()
            }
            GlassIconDropdownMenu(
                show = menuShow,
                onDismissRequest = { menuShow = false },
                anchor = menuAnchor,
                anchorContent = { AvatarContent() },
                entries = listOf(menuEntry),
                backdrop = LocalBackdrop.current,
                visuals = liquidGlassPopupVisuals(),
            )
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
        ) {
            Icon(imageVector = MiuixIcons.RemoveContact, contentDescription = null)
        }
    }
}
