package com.dyzyks.montager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.ui.theme.*

@Composable
fun ExportSheetTab(
    isExporting: Boolean,
    progress: Float,
    onStartExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRes by remember { mutableStateOf("1080p (1920x1080)") }
    var selectedFps by remember { mutableStateOf("60 FPS (Ultra Smooth)") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelBackground)
            .padding(14.dp)
            .testTag("export_sheet_tab")
    ) {
        Text(
            text = "DELIVER / EXPORT SETTINGS",
            color = ResolveBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Preset card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ResolveElevated)
                .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "FORMAT: MP4 / H.264 High Profile",
                color = ResolveTextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AUDIO: AAC-LC Stereo / 192 kbps / 44.1 kHz",
                color = ResolveMutedText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "DESTINATION: Movies/DYZYKS",
                color = ResolveGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isExporting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ResolveElevated)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ENCODING MONTAGE: ${(progress * 100).toInt()}%",
                    color = PlayheadOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PlayheadOrange,
                    trackColor = BorderHairline
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Running in foreground service. Check notifications.",
                    color = ResolveMutedText,
                    fontSize = 10.sp
                )
            }
        } else {
            Button(
                onClick = onStartExport,
                colors = ButtonDefaults.buttonColors(containerColor = ResolveBlue),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("start_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export",
                    tint = ResolveTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RENDER & EXPORT TO MOVIES/DYZYKS",
                    color = ResolveTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
