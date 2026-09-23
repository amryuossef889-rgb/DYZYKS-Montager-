package com.dyzyks.montager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyzyks.montager.ui.components.*
import com.dyzyks.montager.ui.theme.*

@Composable
fun MontagerScreen(
    viewModel: MontagerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val timelineZoom by viewModel.timelineZoom.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    val selectedClip = remember(project, selectedClipId) {
        if (selectedClipId != null) project.findClip(selectedClipId!!) else null
    }

    // Root Container with DaVinci Resolve Dark Styling & Hardware Keyboard Handler (Phase 8)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResolveBlack)
            .onKeyEvent { keyEvent ->
                val action = com.dyzyks.montager.input.ShortcutDispatcher.resolveKeyEvent(keyEvent)
                if (action != null) {
                    com.dyzyks.montager.input.ShortcutDispatcher.dispatch(action, viewModel)
                } else false
            }
            .testTag("montager_screen")
    ) {
        // TOP RESOLVE HEADER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBackground)
                .border(0.5.dp, BorderHairline)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DYZYKS",
                    color = PlayheadOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MONTAGER",
                    color = ResolveTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "• ${project.name}",
                    color = ResolveMutedText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Undo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = viewModel.canUndo,
                    modifier = Modifier.size(32.dp).testTag("action_undo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (viewModel.canUndo) ResolveTextPrimary else ResolveMutedText.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = viewModel.canRedo,
                    modifier = Modifier.size(32.dp).testTag("action_redo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (viewModel.canRedo) ResolveTextPrimary else ResolveMutedText.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Quick Export Button
                Button(
                    onClick = {
                        viewModel.setActiveTab(ActiveTab.EXPORT)
                        viewModel.startExport(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResolveBlue),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.height(28.dp).testTag("header_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Quick Export",
                        tint = ResolveTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EXPORT", color = ResolveTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // TOP SECTION: Viewer & Transport
        ViewerSection(
            currentFrame = playerState.currentFrame,
            playheadUs = playerState.playheadUs,
            durationUs = project.durationUs,
            fps = project.fps,
            isPlaying = playerState.isPlaying,
            onTogglePlay = { viewModel.togglePlayPause() },
            onStepFrame = { delta -> viewModel.stepFrame(delta) },
            onJumpStart = { viewModel.jumpToStart() },
            onJumpEnd = { viewModel.jumpToEnd() }
        )

        // WORKSPACE TAB BAR (DaVinci Resolve Bottom-style Pages)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ResolveElevated)
                .border(0.5.dp, BorderHairline),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf(
                Triple(ActiveTab.MEDIA_POOL, "MEDIA", Icons.Default.FolderOpen),
                Triple(ActiveTab.EFFECTS, "EFFECTS", Icons.Default.AutoAwesome),
                Triple(ActiveTab.INSPECTOR, "INSPECTOR", Icons.Default.Tune),
                Triple(ActiveTab.EXPORT, "DELIVER", Icons.Default.RocketLaunch)
            )

            tabs.forEach { (tab, title, icon) ->
                val isSelected = (activeTab == tab)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setActiveTab(tab) }
                        .background(if (isSelected) PanelBackground else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) BorderHairline else Color.Transparent
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) ResolveBlue else ResolveMutedText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        color = if (isSelected) ResolveTextPrimary else ResolveMutedText,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // MIDDLE PANEL: Active Workspace Tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .background(PanelBackground)
        ) {
            when (activeTab) {
                ActiveTab.MEDIA_POOL -> MediaPoolTab(
                    onAddMedia = { name, type, durUs ->
                        viewModel.addDemoMediaClip(name, type, durUs)
                    }
                )
                ActiveTab.EFFECTS -> EffectsLibraryTab(
                    onAddEffect = { effectType ->
                        viewModel.addEffectToSelected(effectType)
                    }
                )
                ActiveTab.INSPECTOR -> InspectorTab(
                    selectedClip = selectedClip,
                    onUpdateTransform = { viewModel.updateSelectedTransform(it) },
                    onUpdateColorGrading = { viewModel.updateSelectedColorGrading(it) },
                    onUpdateAudioSettings = { viewModel.updateSelectedAudioSettings(it) }
                )
                ActiveTab.EXPORT -> ExportSheetTab(
                    isExporting = isExporting,
                    progress = exportProgress,
                    onStartExport = { viewModel.startExport(context) }
                )
            }
        }

        // BOTTOM SECTION: Multi-track Timeline
        TimelineSection(
            project = project,
            playheadUs = playerState.playheadUs,
            selectedClipId = selectedClipId,
            timelineZoom = timelineZoom,
            onZoomChange = { viewModel.setTimelineZoom(it) },
            onSelectClip = { viewModel.selectClip(it) },
            onScrubTo = { viewModel.scrubTo(it) },
            onSplit = { viewModel.splitAtPlayhead() },
            onRippleDelete = { viewModel.rippleDeleteSelected() },
            onLiftDelete = { viewModel.liftDeleteSelected() },
            onTrimSelected = { inDelta, outDelta -> viewModel.trimSelectedClip(inDelta, outDelta) },
            onSpeedSelected = { viewModel.changeSelectedClipSpeed(it) },
            onToggleMute = { viewModel.toggleTrackMute(it) },
            onToggleSolo = { viewModel.toggleTrackSolo(it) },
            onToggleLock = { viewModel.toggleTrackLock(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        )
    }
}
