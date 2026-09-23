package com.dyzyks.montager.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dyzyks.montager.engine.TimelineEditingEngine
import com.dyzyks.montager.export.ExportService
import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.*
import com.dyzyks.montager.player.PreviewPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ActiveTab {
    MEDIA_POOL,
    EFFECTS,
    INSPECTOR,
    EXPORT
}

class MontagerViewModel(application: Application) : AndroidViewModel(application) {

    private val undoRedoManager = UndoRedoManager(maxHistorySize = 50)

    private val _project = MutableStateFlow(createInitialDemoProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>("clip-v1-demo")
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _activeTab = MutableStateFlow(ActiveTab.INSPECTOR)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private var renderPlan = TimelineRenderPlan(_project.value)
    val player = PreviewPlayer(renderPlan, viewModelScope)

    val playerState = player.state

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0.0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f) // Zoom factor: 1.0f = 100dp per second
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun setTimelineZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.2f, 4.0f)
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
        applyProjectUpdate(TimelineEditingEngine.setSelection(_project.value, if (clipId != null) setOf(clipId) else emptySet()), recordUndo = false)
        if (clipId != null) {
            _activeTab.value = ActiveTab.INSPECTOR
        }
    }

    // Playback Controls
    fun togglePlayPause() = player.togglePlayPause()

    fun scrubTo(timelineUs: Long) = player.scrubTo(timelineUs)

    fun stepFrame(deltaFrames: Int) {
        val currentUs = player.state.value.playheadUs
        val frameUs = 1_000_000L / _project.value.fps
        val newUs = (currentUs + deltaFrames * frameUs).coerceIn(0L, _project.value.durationUs)
        player.scrubTo(newUs)
    }

    fun jumpToStart() = player.scrubTo(0L)
    fun jumpToEnd() = player.scrubTo(_project.value.durationUs)

    // Editing operations
    fun splitAtPlayhead() {
        val playheadUs = player.state.value.playheadUs
        val targetClipId = _selectedClipId.value ?: findClipUnderPlayhead(playheadUs)?.id ?: return

        recordUndo()
        val updated = TimelineEditingEngine.splitClip(_project.value, targetClipId, playheadUs)
        applyProjectUpdate(updated)
    }

    fun rippleDeleteSelected() {
        val id = _selectedClipId.value ?: return
        recordUndo()
        val updated = TimelineEditingEngine.rippleDelete(_project.value, setOf(id))
        _selectedClipId.value = null
        applyProjectUpdate(updated)
    }

    fun liftDeleteSelected() {
        val id = _selectedClipId.value ?: return
        recordUndo()
        val updated = TimelineEditingEngine.liftDelete(_project.value, setOf(id))
        _selectedClipId.value = null
        applyProjectUpdate(updated)
    }

    fun trimSelectedClip(sourceInDeltaUs: Long = 0L, sourceOutDeltaUs: Long = 0L) {
        val id = _selectedClipId.value ?: return
        val clip = _project.value.findClip(id) ?: return
        recordUndo()
        val updated = TimelineEditingEngine.trimClip(
            _project.value,
            id,
            newSourceInUs = clip.sourceInUs + sourceInDeltaUs,
            newSourceOutUs = clip.sourceOutUs + sourceOutDeltaUs
        )
        applyProjectUpdate(updated)
    }

    fun changeSelectedClipSpeed(newSpeed: Float) {
        val id = _selectedClipId.value ?: return
        recordUndo()
        val updated = TimelineEditingEngine.changeClipSpeed(_project.value, id, newSpeed, ripple = true)
        applyProjectUpdate(updated)
    }

    fun addEffectToSelected(type: EffectType) {
        val id = _selectedClipId.value ?: return
        val clip = _project.value.findClip(id) ?: return
        recordUndo()

        val effect = Effect(
            type = type,
            startTimeUs = 0L,
            durationUs = minOf(clip.durationUs, 2_000_000L),
            intensity = 0.7f
        )
        val updatedClip = clip.copy(effects = clip.effects + effect)
        val updatedTracks = _project.value.tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == id) updatedClip else it })
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks))
    }

    fun updateSelectedTransform(transform: Transform) {
        val id = _selectedClipId.value ?: return
        val clip = _project.value.findClip(id) ?: return
        val updatedClip = clip.copy(transform = transform)
        val updatedTracks = _project.value.tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == id) updatedClip else it })
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun updateSelectedColorGrading(grading: ColorGrading) {
        val id = _selectedClipId.value ?: return
        val clip = _project.value.findClip(id) ?: return
        val updatedClip = clip.copy(colorGrading = grading)
        val updatedTracks = _project.value.tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == id) updatedClip else it })
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun updateSelectedAudioSettings(settings: AudioSettings) {
        val id = _selectedClipId.value ?: return
        val clip = _project.value.findClip(id) ?: return
        val updatedClip = clip.copy(audioSettings = settings)
        val updatedTracks = _project.value.tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == id) updatedClip else it })
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun toggleTrackMute(trackId: String) {
        val updatedTracks = _project.value.tracks.map {
            if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun toggleTrackSolo(trackId: String) {
        val updatedTracks = _project.value.tracks.map {
            if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun toggleTrackLock(trackId: String) {
        val updatedTracks = _project.value.tracks.map {
            if (it.id == trackId) it.copy(isLocked = !it.isLocked) else it
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks), recordUndo = false)
    }

    fun addDemoMediaClip(name: String, mediaType: MediaType, durationUs: Long = 4_000_000L) {
        recordUndo()
        val targetTrackId = if (mediaType == MediaType.VIDEO) "V1" else "A1"
        val track = _project.value.tracks.firstOrNull { it.id == targetTrackId } ?: return
        val startUs = track.durationUs

        val newClip = TimelineClip(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceUri = "/demo/$name",
            mediaType = mediaType,
            timelineStartUs = startUs,
            durationUs = durationUs,
            sourceInUs = 0L,
            sourceOutUs = durationUs
        )

        val updatedTracks = _project.value.tracks.map {
            if (it.id == targetTrackId) it.copy(clips = it.clips + newClip) else it
        }
        applyProjectUpdate(_project.value.copy(tracks = updatedTracks))
        selectClip(newClip.id)
    }

    // Undo / Redo
    fun undo() {
        val prev = undoRedoManager.undo(_project.value) ?: return
        applyProjectUpdate(prev, recordUndo = false)
    }

    fun redo() {
        val next = undoRedoManager.redo(_project.value) ?: return
        applyProjectUpdate(next, recordUndo = false)
    }

    val canUndo: Boolean get() = undoRedoManager.canUndo
    val canRedo: Boolean get() = undoRedoManager.canRedo

    // Export Trigger
    fun startExport(context: Context) {
        ExportService.activePlan = renderPlan
        val intent = Intent(context, ExportService::class.java).apply {
            action = ExportService.ACTION_START_EXPORT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun recordUndo() {
        undoRedoManager.pushState(_project.value)
    }

    private fun applyProjectUpdate(newProject: Project, recordUndo: Boolean = false) {
        if (recordUndo) {
            undoRedoManager.pushState(_project.value)
        }
        _project.value = newProject
        renderPlan = TimelineRenderPlan(newProject)
        player.updateRenderPlan(renderPlan)
    }

    private fun findClipUnderPlayhead(timelineUs: Long): TimelineClip? {
        for (track in _project.value.tracks) {
            val clip = track.getClipAt(timelineUs)
            if (clip != null) return clip
        }
        return null
    }

    private fun createInitialDemoProject(): Project {
        val clip1 = TimelineClip(
            id = "clip-v1-demo",
            name = "Ace_Clutch_4K.mp4",
            sourceUri = "/demo/ace_clutch.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 0L,
            durationUs = 6_000_000L,
            effects = listOf(
                Effect(type = EffectType.RGB_SPLIT, startTimeUs = 2_000_000L, durationUs = 1_000_000L, intensity = 0.8f)
            ),
            textOverlay = TextOverlay(text = "INSANE 1v5 CLUTCH", fontSizeSp = 34f, positionY = 0.65f),
            isSelected = true
        )
        val clip2 = TimelineClip(
            id = "clip-v1-sniper",
            name = "Awa_NoScope.mp4",
            sourceUri = "/demo/sniper.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 6_000_000L,
            durationUs = 4_000_000L,
            effects = listOf(
                Effect(type = EffectType.ZOOM_PULSE, startTimeUs = 1_000_000L, durationUs = 800_000L, intensity = 0.9f)
            )
        )
        val audioClip1 = TimelineClip(
            id = "clip-a1-trap",
            name = "Gaming_Phonk_Beat.wav",
            sourceUri = "/demo/beat.wav",
            mediaType = MediaType.AUDIO,
            timelineStartUs = 0L,
            durationUs = 10_000_000L,
            audioSettings = AudioSettings(volume = 0.9f, pan = 0.0f)
        )

        return Project(
            name = "Valorant Montage #1",
            fps = 60,
            tracks = listOf(
                Track(id = "V2", name = "V2", type = TrackType.VIDEO, clips = emptyList()),
                Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip1, clip2)),
                Track(id = "A1", name = "A1", type = TrackType.AUDIO, clips = listOf(audioClip1)),
                Track(id = "A2", name = "A2", type = TrackType.AUDIO, clips = emptyList())
            )
        )
    }

    override fun onCleared() {
        player.close()
        super.onCleared()
    }
}
