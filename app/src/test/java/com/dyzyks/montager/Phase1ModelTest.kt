package com.dyzyks.montager

import com.dyzyks.montager.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1ModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTimecodeFormattingAndCalculations() {
        val fps = 60
        val oneSecondUs = 1_000_000L
        assertEquals(60L, Timecode.usToFrames(oneSecondUs, fps))
        assertEquals(oneSecondUs, Timecode.framesToUs(60L, fps))

        // 1 hour, 23 minutes, 45 seconds, 15 frames at 60fps
        val targetUs = (3600L * 1 + 60L * 23 + 45) * 1_000_000L + (15 * 1_000_000L / 60)
        val formatted = Timecode.formatTimecode(targetUs, fps)
        assertEquals("01:23:45:15", formatted)

        assertEquals("00:00:00:00", Timecode.formatTimecode(0L, fps))
        assertEquals("00:01.5", Timecode.formatShort(1_500_000L))
    }

    @Test
    fun testKeyframeInterpolation() {
        val keyframes = listOf(
            Keyframe(timeUs = 0L, value = 0.0f, easing = EasingType.LINEAR),
            Keyframe(timeUs = 1_000_000L, value = 100.0f, easing = EasingType.LINEAR)
        )
        val prop = KeyframedProperty("scale", 1.0f, keyframes)

        assertEquals(0.0f, prop.getValueAt(0L), 0.001f)
        assertEquals(50.0f, prop.getValueAt(500_000L), 0.001f)
        assertEquals(100.0f, prop.getValueAt(1_000_000L), 0.001f)
        assertEquals(100.0f, prop.getValueAt(2_000_000L), 0.001f) // clamped to last
        assertEquals(0.0f, prop.getValueAt(-100L), 0.001f) // clamped to first
    }

    @Test
    fun testAudioSettingsPanAndGain() {
        // Unity gain at center (pan = 0.0f)
        val centerAudio = AudioSettings(volume = 1.0f, gainDb = 0.0f, pan = 0.0f)
        val (cL, cR) = centerAudio.computePanGains()
        assertEquals(1.0f, cL, 0.001f)
        assertEquals(1.0f, cR, 0.001f)
        assertEquals(1.0f, centerAudio.computeLinearGain(), 0.001f)

        // Full left pan
        val leftAudio = AudioSettings(pan = -1.0f)
        val (lL, lR) = leftAudio.computePanGains()
        assertEquals(1.0f, lL, 0.001f)
        assertEquals(0.0f, lR, 0.001f)

        // Full right pan
        val rightAudio = AudioSettings(pan = 1.0f)
        val (rL, rR) = rightAudio.computePanGains()
        assertEquals(0.0f, rL, 0.001f)
        assertEquals(1.0f, rR, 0.001f)

        // dB gain conversion (+6dB approx 2.0x, -6dB approx 0.5x)
        val plus6Db = AudioSettings(gainDb = 6.0206f)
        assertEquals(2.0f, plus6Db.computeLinearGain(), 0.01f)

        // Mute overrides gain
        val muted = AudioSettings(isMuted = true, volume = 2.0f)
        assertEquals(0.0f, muted.computeLinearGain(), 0.001f)

        // Fades
        val fadedAudio = AudioSettings(fadeInUs = 1_000_000L, fadeOutUs = 1_000_000L)
        val clipDuration = 4_000_000L
        assertEquals(0.0f, fadedAudio.computeFadeMultiplier(0L, clipDuration), 0.001f)
        assertEquals(0.5f, fadedAudio.computeFadeMultiplier(500_000L, clipDuration), 0.001f)
        assertEquals(1.0f, fadedAudio.computeFadeMultiplier(2_000_000L, clipDuration), 0.001f)
        assertEquals(0.5f, fadedAudio.computeFadeMultiplier(3_500_000L, clipDuration), 0.001f)
        assertEquals(0.0f, fadedAudio.computeFadeMultiplier(4_000_000L, clipDuration), 0.001f)
    }

    @Test
    fun testProjectSerializationRoundTrip() {
        val clip1 = TimelineClip(
            id = "clip-1",
            name = "gameplay_4k.mp4",
            sourceUri = "/storage/emulated/0/Movies/gameplay.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 6_000_000L,
            speed = 1.0f,
            transform = Transform(
                positionX = 0.1f,
                positionY = -0.2f,
                scaleX = 1.2f,
                scaleY = 1.2f,
                rotationDeg = 15.0f,
                opacity = 0.9f,
                keyframes = listOf(
                    Keyframe(timeUs = 0L, value = 0.0f, easing = EasingType.EASE_IN_OUT),
                    Keyframe(timeUs = 5_000_000L, value = 1.0f, easing = EasingType.LINEAR)
                )
            ),
            colorGrading = ColorGrading(
                brightness = 0.05f,
                contrast = 1.15f,
                saturation = 1.3f,
                temperature = 0.1f,
                exposure = 0.2f
            ),
            audioSettings = AudioSettings(
                volume = 0.8f,
                gainDb = 2.0f,
                pan = -0.25f,
                fadeInUs = 200_000L,
                fadeOutUs = 300_000L
            ),
            effects = listOf(
                Effect(
                    id = "eff-1",
                    type = EffectType.RGB_SPLIT,
                    startTimeUs = 1_000_000L,
                    durationUs = 500_000L,
                    intensity = 0.75f,
                    parameters = mapOf("splitOffset" to 12.0f)
                )
            ),
            transitionIn = Transition(
                id = "tr-1",
                type = TransitionType.CROSSFADE,
                durationUs = 400_000L
            ),
            textOverlay = TextOverlay(
                id = "txt-1",
                text = "EPIC CLUTCH",
                fontSizeSp = 42.0f,
                positionY = 0.7f
            ),
            groupId = "group-sync-1"
        )

        val project = Project(
            id = "proj-101",
            name = "Valorant Montage #1",
            width = 1920,
            height = 1080,
            fps = 60,
            tracks = listOf(
                Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip1)),
                Track(id = "A1", name = "A1", type = TrackType.AUDIO, clips = emptyList())
            )
        )

        // Serialize
        val json = ProjectSerializer.toJson(project)
        assertNotNull(json)

        // Deserialize
        val deserialized = ProjectSerializer.fromJson(json)

        assertEquals(project.id, deserialized.id)
        assertEquals(project.name, deserialized.name)
        assertEquals(project.width, deserialized.width)
        assertEquals(project.height, deserialized.height)
        assertEquals(project.fps, deserialized.fps)
        assertEquals(2, deserialized.tracks.size)

        val restoredClip = deserialized.tracks[0].clips[0]
        assertEquals("clip-1", restoredClip.id)
        assertEquals("gameplay_4k.mp4", restoredClip.name)
        assertEquals(1.2f, restoredClip.transform.scaleX, 0.001f)
        assertEquals(15.0f, restoredClip.transform.rotationDeg, 0.001f)
        assertEquals(2, restoredClip.transform.keyframes.size)
        assertEquals(1.15f, restoredClip.colorGrading.contrast, 0.001f)
        assertEquals(-0.25f, restoredClip.audioSettings.pan, 0.001f)
        assertEquals(1, restoredClip.effects.size)
        assertEquals(EffectType.RGB_SPLIT, restoredClip.effects[0].type)
        assertEquals(12.0f, restoredClip.effects[0].parameters["splitOffset"] ?: 0.0f, 0.001f)
        assertEquals(TransitionType.CROSSFADE, restoredClip.transitionIn?.type)
        assertEquals("EPIC CLUTCH", restoredClip.textOverlay?.text)
        assertEquals("group-sync-1", restoredClip.groupId)
    }

    @Test
    fun testSchemaMigrationFromV1() {
        // Construct raw V1 json without effects and colorGrading
        val v1Json = JSONObject()
        v1Json.put("id", "legacy-proj-1")
        v1Json.put("schemaVersion", 1)
        v1Json.put("name", "Legacy Project")

        val tracksArr = JSONArray()
        val trackObj = JSONObject()
        trackObj.put("id", "V1")
        trackObj.put("name", "V1")
        trackObj.put("type", "VIDEO")

        val clipsArr = JSONArray()
        val clipObj = JSONObject()
        clipObj.put("id", "old-clip")
        clipObj.put("name", "old.mp4")
        clipObj.put("sourceUri", "/path/old.mp4")
        clipObj.put("mediaType", "VIDEO")
        clipObj.put("timelineStartUs", 0L)
        clipObj.put("durationUs", 3_000_000L)
        clipObj.put("sourceInUs", 0L)
        clipObj.put("sourceOutUs", 3_000_000L)
        clipsArr.put(clipObj)

        trackObj.put("clips", clipsArr)
        tracksArr.put(trackObj)
        v1Json.put("tracks", tracksArr)

        val migratedProject = ProjectSerializer.fromJson(v1Json)
        assertEquals(Project.CURRENT_SCHEMA_VERSION, migratedProject.schemaVersion)
        assertEquals(1, migratedProject.tracks[0].clips.size)
        val clip = migratedProject.tracks[0].clips[0]
        assertNotNull(clip.colorGrading)
        assertEquals(1.0f, clip.colorGrading.contrast, 0.001f)
        assertTrue(clip.effects.isEmpty())
    }

    @Test
    fun testAtomicFileSaveAndLoad() {
        val destFile = File(tempFolder.root, "test_project.dyzyks")
        val project = Project(name = "Atomic Save Test")

        ProjectSerializer.saveToFileAtomic(project, destFile)
        assertTrue(destFile.exists())
        assertTrue(destFile.length() > 0)

        val loaded = ProjectSerializer.loadFromFile(destFile)
        assertEquals("Atomic Save Test", loaded.name)
    }

    @Test
    fun testUndoRedoStack() {
        val undoManager = UndoRedoManager(maxHistorySize = 5)

        val state0 = Project(name = "State 0")
        val state1 = Project(name = "State 1")
        val state2 = Project(name = "State 2")

        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        // Apply state 1
        undoManager.pushState(state0)
        assertTrue(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        // Apply state 2
        undoManager.pushState(state1)
        assertEquals(2, undoManager.undoCount)

        // Undo -> should revert to state 1
        val undone1 = undoManager.undo(state2)
        assertEquals("State 1", undone1?.name)
        assertTrue(undoManager.canUndo)
        assertTrue(undoManager.canRedo)

        // Undo -> should revert to state 0
        val undone0 = undoManager.undo(undone1!!)
        assertEquals("State 0", undone0?.name)
        assertFalse(undoManager.canUndo)
        assertTrue(undoManager.canRedo)

        // Redo -> should move forward to state 1
        val redone1 = undoManager.redo(undone0!!)
        assertEquals("State 1", redone1?.name)
        assertTrue(undoManager.canUndo)
        assertTrue(undoManager.canRedo)

        // Pushing a new state clears redo
        val state3 = Project(name = "State 3")
        undoManager.pushState(redone1!!)
        assertFalse(undoManager.canRedo)
    }
}
