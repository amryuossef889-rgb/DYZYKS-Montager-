package com.dyzyks.montager.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.model.Timecode
import com.dyzyks.montager.ui.theme.*

@Composable
fun ViewerSection(
    currentFrame: Bitmap?,
    playheadUs: Long,
    durationUs: Long,
    fps: Int,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onStepFrame: (Int) -> Unit,
    onJumpStart: () -> Unit,
    onJumpEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .padding(8.dp)
    ) {
        // 16:9 Video Monitor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(ResolveBlack)
                .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
                .testTag("video_monitor"),
            contentAlignment = Alignment.Center
        ) {
            if (currentFrame != null && !currentFrame.isRecycled) {
                Image(
                    bitmap = currentFrame.asImageBitmap(),
                    contentDescription = "Video Monitor Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "NO SIGNAL / NO MEDIA",
                    color = ResolveMutedText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            // Top-left Rec / Timecode badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(ResolveElevated.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isPlaying) "PLAY 60.0 FPS" else "STOPPED",
                    color = if (isPlaying) ResolveGreen else PlayheadOrange,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Timecode Readout + Transport Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // DaVinci Timecode readout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Timecode.formatTimecode(playheadUs, fps),
                    color = PlayheadOrange,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("timecode_current")
                )
                Text(
                    text = " / ${Timecode.formatTimecode(durationUs, fps)}",
                    color = ResolveMutedText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Transport Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onJumpStart,
                    modifier = Modifier.size(36.dp).testTag("transport_jump_start")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Jump to Start",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onStepFrame(-1) },
                    modifier = Modifier.size(36.dp).testTag("transport_step_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Step -1 Frame",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play / Pause prominent button
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) ResolveElevated else ResolveBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("transport_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onStepFrame(1) },
                    modifier = Modifier.size(36.dp).testTag("transport_step_forward")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Step +1 Frame",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onJumpEnd,
                    modifier = Modifier.size(36.dp).testTag("transport_jump_end")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Jump to End",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
