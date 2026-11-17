package com.clipr.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.ui.theme.Graphite
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed
import kotlinx.coroutines.delay

/**
 * Lifecycle-aware AndroidX Media3 ExoPlayer video preview surface (Section 03, 05 & Phase 4).
 * Supports both full-source preview and bounded [clipStartMs, clipEndMs] highlight preview.
 */
@Composable
fun CliprExoPlayerPreview(
    sourceUri: String,
    totalDurationMs: Long,
    modifier: Modifier = Modifier,
    clipStartMs: Long = 0L,
    clipEndMs: Long = totalDurationMs
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(clipStartMs) }

    val effectiveEndMs = remember(totalDurationMs, clipEndMs) {
        if (clipEndMs > clipStartMs) clipEndMs else totalDurationMs.coerceAtLeast(1000L)
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Bind MediaItem and optional clipping window whenever sourceUri or bounds change
    LaunchedEffect(sourceUri, clipStartMs, effectiveEndMs) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(effectiveEndMs)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        currentPositionMs = clipStartMs
    }

    // Poll playback position smoothly while playing
    LaunchedEffect(isPlaying, exoPlayer) {
        while (isPlaying) {
            currentPositionMs = (clipStartMs + exoPlayer.currentPosition).coerceIn(clipStartMs, effectiveEndMs)
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                isPlaying = false
            }
            delay(100L)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(playerListener)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Graphite)
    ) {
        CliprFrameBracketBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            backgroundColor = ObsidianBlack
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom Flat CLIPR Transport & Scrubber Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { targetMs ->
                    val clamped = targetMs.toLong().coerceIn(clipStartMs, effectiveEndMs)
                    currentPositionMs = clamped
                    exoPlayer.seekTo((clamped - clipStartMs).coerceAtLeast(0L))
                },
                valueRange = clipStartMs.toFloat()..effectiveEndMs.coerceAtLeast(clipStartMs + 100L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = SignalRed,
                    activeTrackColor = SignalRed,
                    inactiveTrackColor = HairlineDivider
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                                exoPlayer.play()
                            } else if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                            tint = SignalRed
                        )
                    }

                    IconButton(
                        onClick = {
                            exoPlayer.seekTo(0L)
                            currentPositionMs = clipStartMs
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Replay,
                            contentDescription = "Restart preview",
                            tint = PorcelainWhite
                        )
                    }

                    Text(
                        text = "${VideoSourceMetadata.formatDurationMs(currentPositionMs)} / ${VideoSourceMetadata.formatDurationMs(effectiveEndMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PorcelainWhite
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MEDIA3 EXOPLAYER",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryGrey
                    )
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                            contentDescription = "Toggle audio mute",
                            tint = if (isMuted) SignalRed else PorcelainWhite
                        )
                    }
                }
            }
        }
    }
}
