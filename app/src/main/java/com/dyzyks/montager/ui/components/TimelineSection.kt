package com.dyzyks.montager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.model.*
import com.dyzyks.montager.ui.theme.*

@Composable
fun TimelineSection(
    project: Project,
    playheadUs: Long,
    selectedClipId: String?,
    timelineZoom: Float,
    onZoomChange: (Float) -> Unit,
    onSelectClip: (String?) -> Unit,
    onScrubTo: (Long) -> Unit,
    onSplit: () -> Unit,
    onRippleDelete: () -> Unit,
    onLiftDelete: () -> Unit,
    onTrimSelected: (inDeltaUs: Long, outDeltaUs: Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val totalSeconds = (project.durationUs / 1_000_000f).coerceAtLeast(10f)
    // 1 second = 80.dp * zoom
    val pxPerSec = 80.dp * timelineZoom

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ResolveBlack)
            .border(1.dp, BorderHairline)
            .testTag("timeline_section")
    ) {
        // TOP TOOLBAR: Quick Editing Strip (Blade, Delete, Trim, Speed, Zoom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ResolveElevated)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Split / Blade tool
                Button(
                    onClick = onSplit,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelBackground),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.height(28.dp).testTag("tool_split")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Split (Blade)",
                        tint = PlayheadOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SPLIT", color = ResolveTextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Ripple Delete
                Button(
                    onClick = onRippleDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelBackground),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.height(28.dp).testTag("tool_ripple_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Ripple Delete",
                        tint = ResolveBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("RIPPLE DEL", color = ResolveTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Lift Delete
                Button(
                    onClick = onLiftDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelBackground),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.height(28.dp).testTag("tool_lift_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Lift Delete",
                        tint = ResolveMutedText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("LIFT", color = ResolveMutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Zoom Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(timelineZoom - 0.2f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, "Zoom Out", tint = ResolveTextPrimary, modifier = Modifier.size(14.dp))
                }
                Text(
                    text = "${(timelineZoom * 100).toInt()}%",
                    color = ResolveMutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = { onZoomChange(timelineZoom + 0.2f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, "Zoom In", tint = ResolveTextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // TIMELINE BODY: Left headers + Right multi-track horizontal scroll
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Track Headers Column
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(PanelBackground)
                    .border(1.dp, BorderHairline)
            ) {
                // Ruler placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(ResolveElevated)
                        .border(1.dp, BorderHairline),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TRACK", color = ResolveMutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }

                // Headers for each track
                project.tracks.forEach { track ->
                    TrackHeaderRow(
                        track = track,
                        onToggleMute = { onToggleMute(track.id) },
                        onToggleSolo = { onToggleSolo(track.id) },
                        onToggleLock = { onToggleLock(track.id) }
                    )
                }
            }

            // Scrollable Tracks Area + Playhead
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                val totalWidthDp = (totalSeconds * 80f * timelineZoom).dp

                Column(modifier = Modifier.width(totalWidthDp).fillMaxHeight()) {
                    // Time Ruler
                    TimelineRuler(
                        totalSeconds = totalSeconds,
                        timelineZoom = timelineZoom,
                        onSeekTo = onScrubTo
                    )

                    // Track lanes
                    project.tracks.forEach { track ->
                        TrackLane(
                            track = track,
                            timelineZoom = timelineZoom,
                            selectedClipId = selectedClipId,
                            onSelectClip = onSelectClip
                        )
                    }
                }

                // Orange Playhead
                val playheadOffsetSec = playheadUs / 1_000_000f
                val playheadOffsetDp = (playheadOffsetSec * 80f * timelineZoom).dp

                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(PlayheadOrange)
                        .testTag("timeline_playhead")
                )

                // Draggable scrub handle at top of playhead
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp - 8.dp, y = 0.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PlayheadOrange)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaUs = (dragAmount.x / (80f * timelineZoom) * 1_000_000f).toLong()
                                onScrubTo((playheadUs + deltaUs).coerceIn(0L, project.durationUs))
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun TrackHeaderRow(
    track: Track,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onToggleLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(0.5.dp, BorderHairline)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = track.name,
            color = if (track.type == TrackType.VIDEO) ResolveBlue else ResolveGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Row {
            Text(
                text = "M",
                color = if (track.isMuted) PlayheadOrange else ResolveMutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (track.isMuted) ResolveElevated else Color.Transparent)
                    .clickable { onToggleMute() }
                    .padding(2.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "S",
                color = if (track.isSolo) ResolveGreen else ResolveMutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (track.isSolo) ResolveElevated else Color.Transparent)
                    .clickable { onToggleSolo() }
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun TimelineRuler(
    totalSeconds: Float,
    timelineZoom: Float,
    onSeekTo: (Long) -> Unit
) {
    val totalWidthDp = (totalSeconds * 80f * timelineZoom).dp
    Box(
        modifier = Modifier
            .width(totalWidthDp)
            .height(20.dp)
            .background(ResolveElevated)
            .border(0.5.dp, BorderHairline)
            .pointerInput(timelineZoom) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val posSec = change.position.x / (80f * timelineZoom)
                    onSeekTo((posSec * 1_000_000f).toLong().coerceAtLeast(0L))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepPx = 80f * timelineZoom
            val stepSec = 1
            for (sec in 0..totalSeconds.toInt()) {
                val x = sec * stepPx
                drawLine(
                    color = BorderHairline,
                    start = Offset(x, size.height - 8f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
        }
    }
}

@Composable
private fun TrackLane(
    track: Track,
    timelineZoom: Float,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(0.5.dp, BorderHairline)
            .background(if (track.type == TrackType.VIDEO) Color(0xFF141418) else Color(0xFF121614))
    ) {
        track.clips.forEach { clip ->
            val startSec = clip.timelineStartUs / 1_000_000f
            val durSec = clip.durationUs / 1_000_000f
            val startDp = (startSec * 80f * timelineZoom).dp
            val widthDp = (durSec * 80f * timelineZoom).coerceAtLeast(20f).dp
            val isSelected = (clip.id == selectedClipId)

            val clipBg = when (clip.mediaType) {
                MediaType.VIDEO -> Color(0xFF233D5E)
                MediaType.AUDIO -> Color(0xFF1B4E3B)
                MediaType.TEXT -> Color(0xFF4A2A6B)
            }

            Box(
                modifier = Modifier
                    .offset(x = startDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(clipBg)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ResolveBlue else BorderHairline,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("clip_${clip.id}")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = clip.name,
                        color = ResolveTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (clip.speed != 1.0f) {
                        Text(
                            text = "${clip.speed}x",
                            color = PlayheadOrange,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (clip.effects.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "FX",
                            color = ResolveGreen,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
