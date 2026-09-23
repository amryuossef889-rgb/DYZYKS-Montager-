package com.dyzyks.montager

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.test.core.app.ApplicationProvider
import com.dyzyks.montager.input.EditorAction
import com.dyzyks.montager.input.ShortcutDispatcher
import com.dyzyks.montager.ui.ActiveTab
import com.dyzyks.montager.ui.MontagerViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase7And8Test {

    private fun createKeyEvent(keyCode: Int, isShift: Boolean = false, isCtrl: Boolean = false): KeyEvent {
        var metaState = 0
        if (isShift) metaState = metaState or android.view.KeyEvent.META_SHIFT_ON
        if (isCtrl) metaState = metaState or android.view.KeyEvent.META_CTRL_ON

        val nativeEvent = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, metaState
        )
        return KeyEvent(nativeEvent)
    }

    @Test
    fun testShortcutDispatcherMappings() {
        // Spacebar -> Toggle play/pause
        val spaceEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_SPACE)
        assertEquals(EditorAction.TOGGLE_PLAY_PAUSE, ShortcutDispatcher.resolveKeyEvent(spaceEvent))

        // B -> Blade Split
        val bEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_B)
        assertEquals(EditorAction.BLADE_SPLIT, ShortcutDispatcher.resolveKeyEvent(bEvent))

        // J / K / L -> Shuttle
        val jEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_J)
        assertEquals(EditorAction.SHUTTLE_REVERSE, ShortcutDispatcher.resolveKeyEvent(jEvent))

        val kEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_K)
        assertEquals(EditorAction.SHUTTLE_PAUSE, ShortcutDispatcher.resolveKeyEvent(kEvent))

        val lEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_L)
        assertEquals(EditorAction.SHUTTLE_FORWARD, ShortcutDispatcher.resolveKeyEvent(lEvent))

        // Del -> Ripple Delete
        val delEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_DEL)
        assertEquals(EditorAction.RIPPLE_DELETE, ShortcutDispatcher.resolveKeyEvent(delEvent))

        // Shift + Del -> Lift Delete
        val shiftDelEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_DEL, isShift = true)
        assertEquals(EditorAction.LIFT_DELETE, ShortcutDispatcher.resolveKeyEvent(shiftDelEvent))

        // Ctrl + Z -> Undo
        val undoEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_Z, isCtrl = true)
        assertEquals(EditorAction.UNDO, ShortcutDispatcher.resolveKeyEvent(undoEvent))

        // Ctrl + Shift + Z -> Redo
        val redoEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_Z, isCtrl = true, isShift = true)
        assertEquals(EditorAction.REDO, ShortcutDispatcher.resolveKeyEvent(redoEvent))

        // Left / Right arrows -> Step frame
        val leftEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(EditorAction.STEP_FRAME_BACK, ShortcutDispatcher.resolveKeyEvent(leftEvent))

        val rightEvent = createKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(EditorAction.STEP_FRAME_FORWARD, ShortcutDispatcher.resolveKeyEvent(rightEvent))
    }

    @Test
    fun testViewModelStateAndEditingFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MontagerViewModel(app)

        // Initial project has tracks and demo clips
        val initialProject = vm.project.value
        assertEquals("Valorant Montage #1", initialProject.name)
        assertTrue(initialProject.tracks.size >= 4)

        // Tab switching
        vm.setActiveTab(ActiveTab.EFFECTS)
        assertEquals(ActiveTab.EFFECTS, vm.activeTab.value)

        // Zoom clamping
        vm.setTimelineZoom(10.0f)
        assertEquals(4.0f, vm.timelineZoom.value, 0.001f)
        vm.setTimelineZoom(0.01f)
        assertEquals(0.2f, vm.timelineZoom.value, 0.001f)
        vm.setTimelineZoom(1.0f)

        // Selection
        vm.selectClip("clip-v1-demo")
        assertEquals("clip-v1-demo", vm.selectedClipId.value)

        // Split at 2.0s
        vm.scrubTo(2_000_000L)
        vm.splitAtPlayhead()
        val afterSplit = vm.project.value
        val v1Clips = afterSplit.tracks.first { it.id == "V1" }.clips
        assertTrue("Expected 3 clips after splitting clip-v1-demo, but got ${v1Clips.size}", v1Clips.size >= 3)

        // Undo
        assertTrue(vm.canUndo)
        vm.undo()
        val afterUndo = vm.project.value
        val v1ClipsUndo = afterUndo.tracks.first { it.id == "V1" }.clips
        assertEquals(2, v1ClipsUndo.size)

        // Redo
        assertTrue(vm.canRedo)
        vm.redo()
        val afterRedo = vm.project.value
        val v1ClipsRedo = afterRedo.tracks.first { it.id == "V1" }.clips
        assertEquals(3, v1ClipsRedo.size)
    }
}
