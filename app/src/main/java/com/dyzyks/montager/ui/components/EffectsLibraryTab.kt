package com.dyzyks.montager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.model.EffectType
import com.dyzyks.montager.ui.theme.*

data class EffectItem(
    val type: EffectType,
    val name: String,
    val desc: String,
    val icon: ImageVector
)

@Composable
fun EffectsLibraryTab(
    onAddEffect: (EffectType) -> Unit,
    modifier: Modifier = Modifier
) {
    val effects = listOf(
        EffectItem(EffectType.ZOOM_PULSE, "Zoom Pulse", "Beat-sync camera punch", Icons.Default.ZoomIn),
        EffectItem(EffectType.SHAKE, "Screen Shake", "Kill / sniper recoil impact", Icons.Default.Vibration),
        EffectItem(EffectType.RGB_SPLIT, "RGB Split", "Cyber chromatic aberration", Icons.Default.ColorLens),
        EffectItem(EffectType.GLITCH, "Cyber Glitch", "Digital scanline artifacting", Icons.Default.FlashAuto),
        EffectItem(EffectType.FLASH, "Impact Flash", "Blinding white kill flash", Icons.Default.FlashOn),
        EffectItem(EffectType.VIGNETTE, "Vignette", "Cinematic radial edge burn", Icons.Default.BrightnessMedium),
        EffectItem(EffectType.COLOR_GRADING, "Cinematic LUT", "Warm gaming grade", Icons.Default.Palette)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelBackground)
            .padding(10.dp)
            .testTag("effects_library_tab")
    ) {
        Text(
            text = "GAMING EFFECTS & PRESETS (Tap to Add)",
            color = ResolveBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(effects) { item ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ResolveElevated)
                        .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
                        .clickable { onAddEffect(item.type) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.name,
                        tint = ResolveBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.name,
                            color = ResolveTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.desc,
                            color = ResolveMutedText,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}
