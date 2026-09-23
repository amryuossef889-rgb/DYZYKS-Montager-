package com.dyzyks.montager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.model.AudioSettings
import com.dyzyks.montager.model.ColorGrading
import com.dyzyks.montager.model.TimelineClip
import com.dyzyks.montager.model.Transform
import com.dyzyks.montager.ui.theme.*

@Composable
fun InspectorTab(
    selectedClip: TimelineClip?,
    onUpdateTransform: (Transform) -> Unit,
    onUpdateColorGrading: (ColorGrading) -> Unit,
    onUpdateAudioSettings: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedClip == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PanelBackground)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "NO CLIP SELECTED\nTap a clip on the timeline to inspect",
                color = ResolveMutedText,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelBackground)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("inspector_panel")
    ) {
        // Clip Title Header
        Text(
            text = "CLIP: ${selectedClip.name}",
            color = ResolveBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION: TRANSFORM
        Text(
            text = "TRANSFORM & POSITION",
            color = ResolveTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))

        SliderRow(
            label = "SCALE",
            value = selectedClip.transform.scaleX,
            valueRange = 0.5f..2.5f,
            format = "%.2fx",
            onValueChange = { s ->
                onUpdateTransform(selectedClip.transform.copy(scaleX = s, scaleY = s))
            }
        )

        SliderRow(
            label = "ROTATION",
            value = selectedClip.transform.rotationDeg,
            valueRange = -180f..180f,
            format = "%.0f°",
            onValueChange = { r ->
                onUpdateTransform(selectedClip.transform.copy(rotationDeg = r))
            }
        )

        SliderRow(
            label = "OPACITY",
            value = selectedClip.transform.opacity,
            valueRange = 0.0f..1.0f,
            format = "%.0f%%",
            displayMultiplier = 100f,
            onValueChange = { op ->
                onUpdateTransform(selectedClip.transform.copy(opacity = op))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderHairline)

        // SECTION: COLOR GRADING
        Text(
            text = "COLOR WHEELS & GRADING",
            color = ResolveTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))

        SliderRow(
            label = "CONTRAST",
            value = selectedClip.colorGrading.contrast,
            valueRange = 0.5f..2.0f,
            format = "%.2f",
            onValueChange = { c ->
                onUpdateColorGrading(selectedClip.colorGrading.copy(contrast = c))
            }
        )

        SliderRow(
            label = "SATURATION",
            value = selectedClip.colorGrading.saturation,
            valueRange = 0.0f..2.0f,
            format = "%.2f",
            onValueChange = { s ->
                onUpdateColorGrading(selectedClip.colorGrading.copy(saturation = s))
            }
        )

        SliderRow(
            label = "TEMPERATURE",
            value = selectedClip.colorGrading.temperature,
            valueRange = -1.0f..1.0f,
            format = "%.2f",
            onValueChange = { t ->
                onUpdateColorGrading(selectedClip.colorGrading.copy(temperature = t))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderHairline)

        // SECTION: AUDIO
        Text(
            text = "AUDIO FAIRLIGHT",
            color = ResolveTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))

        SliderRow(
            label = "VOLUME",
            value = selectedClip.audioSettings.volume,
            valueRange = 0.0f..2.0f,
            format = "%.2f",
            onValueChange = { vol ->
                onUpdateAudioSettings(selectedClip.audioSettings.copy(volume = vol))
            }
        )

        SliderRow(
            label = "PAN",
            value = selectedClip.audioSettings.pan,
            valueRange = -1.0f..1.0f,
            format = "%.2f",
            onValueChange = { p ->
                onUpdateAudioSettings(selectedClip.audioSettings.copy(pan = p))
            }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: String,
    displayMultiplier: Float = 1.0f,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ResolveMutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(90.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ResolveBlue,
                activeTrackColor = ResolveBlue,
                inactiveTrackColor = ResolveElevated
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = String.format(format, value * displayMultiplier),
            color = ResolveTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp)
        )
    }
}
