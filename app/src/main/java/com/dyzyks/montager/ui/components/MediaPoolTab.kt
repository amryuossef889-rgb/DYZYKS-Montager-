package com.dyzyks.montager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.model.MediaType
import com.dyzyks.montager.ui.theme.*

data class MediaPoolItem(
    val name: String,
    val type: MediaType,
    val durationText: String,
    val durationUs: Long
)

@Composable
fun MediaPoolTab(
    onAddMedia: (name: String, type: MediaType, durationUs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleMedia = listOf(
        MediaPoolItem("Clutch_Operator_Ace.mp4", MediaType.VIDEO, "00:04:00", 4_000_000L),
        MediaPoolItem("Flick_Shot_Headshot.mp4", MediaType.VIDEO, "00:03:00", 3_000_000L),
        MediaPoolItem("Victory_Cinematic_Ending.mp4", MediaType.VIDEO, "00:05:00", 5_000_000L),
        MediaPoolItem("Aggressive_Drill_Beat.wav", MediaType.AUDIO, "00:12:00", 12_000_000L),
        MediaPoolItem("808_Sub_Bass_Drop.wav", MediaType.AUDIO, "00:02:50", 2_500_000L),
        MediaPoolItem("Hitmarker_Double_Ding.wav", MediaType.AUDIO, "00:01:00", 1_000_000L)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelBackground)
            .padding(10.dp)
            .testTag("media_pool_tab")
    ) {
        Text(
            text = "MEDIA BIN & ASSETS (Tap to append to Timeline)",
            color = ResolveBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sampleMedia) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(ResolveElevated)
                        .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
                        .clickable { onAddMedia(item.name, item.type, item.durationUs) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.type == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.Audiotrack,
                        contentDescription = item.type.name,
                        tint = if (item.type == MediaType.VIDEO) ResolveBlue else ResolveGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = ResolveTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.durationText,
                            color = ResolveMutedText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { onAddMedia(item.name, item.type, item.durationUs) },
                        colors = ButtonDefaults.buttonColors(containerColor = ResolveBlack),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = ResolveBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ADD", color = ResolveTextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
