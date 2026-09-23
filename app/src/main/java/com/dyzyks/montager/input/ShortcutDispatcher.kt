package com.dyzyks.montager.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.dyzyks.montager.ui.MontagerViewModel

enum class EditorAction {
    TOGGLE_PLAY_PAUSE,
    SHUTTLE_REVERSE,
    SHUTTLE_PAUSE,
    SHUTTLE_FORWARD,
    MARK_IN,
    MARK_OUT,
    BLADE_SPLIT,
    RIPPLE_DELETE,
    LIFT_DELETE,
    UNDO,
    REDO,
    STEP_FRAME_BACK,
    STEP_FRAME_FORWARD,
    JUMP_START,
    JUMP_END,
    ZOOM_IN,
    ZOOM_OUT
}

object ShortcutDispatcher {

    /**
     * Resolves a hardware keyboard event into an EditorAction following DaVinci Resolve conventions.
     */
    fun resolveKeyEvent(event: KeyEvent): EditorAction? {
        if (event.type != KeyEventType.KeyDown) return null

        val isCmdOrCtrl = event.isCtrlPressed || event.isMetaPressed
        val isShift = event.isShiftPressed

        return when {
            // Undo / Redo
            isCmdOrCtrl && isShift && event.key == Key.Z -> EditorAction.REDO
            isCmdOrCtrl && event.key == Key.Z -> EditorAction.UNDO

            // Space: Play / Pause
            event.key == Key.Spacebar -> EditorAction.TOGGLE_PLAY_PAUSE

            // J / K / L Shuttle
            event.key == Key.J -> EditorAction.SHUTTLE_REVERSE
            event.key == Key.K -> EditorAction.SHUTTLE_PAUSE
            event.key == Key.L -> EditorAction.SHUTTLE_FORWARD

            // B: Blade / Split
            event.key == Key.B -> EditorAction.BLADE_SPLIT

            // In / Out Points
            event.key == Key.I -> EditorAction.MARK_IN
            event.key == Key.O -> EditorAction.MARK_OUT

            // Delete / Backspace: Ripple vs Lift
            (event.key == Key.Delete || event.key == Key.Backspace) && isShift -> EditorAction.LIFT_DELETE
            event.key == Key.Delete || event.key == Key.Backspace -> EditorAction.RIPPLE_DELETE

            // Step Frames
            event.key == Key.DirectionLeft -> EditorAction.STEP_FRAME_BACK
            event.key == Key.DirectionRight -> EditorAction.STEP_FRAME_FORWARD

            // Navigation
            event.key == Key.DirectionUp -> EditorAction.JUMP_START
            event.key == Key.DirectionDown -> EditorAction.JUMP_END

            // Zoom
            event.key == Key.Equals || event.key == Key.Plus -> EditorAction.ZOOM_IN
            event.key == Key.Minus -> EditorAction.ZOOM_OUT

            else -> null
        }
    }

    /**
     * Dispatches an EditorAction to the ViewModel.
     * Returns true if handled.
     */
    fun dispatch(action: EditorAction, viewModel: MontagerViewModel): Boolean {
        when (action) {
            EditorAction.TOGGLE_PLAY_PAUSE -> viewModel.togglePlayPause()
            EditorAction.SHUTTLE_REVERSE -> viewModel.stepFrame(-10)
            EditorAction.SHUTTLE_PAUSE -> viewModel.player.pause()
            EditorAction.SHUTTLE_FORWARD -> viewModel.stepFrame(10)
            EditorAction.MARK_IN -> {}
            EditorAction.MARK_OUT -> {}
            EditorAction.BLADE_SPLIT -> viewModel.splitAtPlayhead()
            EditorAction.RIPPLE_DELETE -> viewModel.rippleDeleteSelected()
            EditorAction.LIFT_DELETE -> viewModel.liftDeleteSelected()
            EditorAction.UNDO -> viewModel.undo()
            EditorAction.REDO -> viewModel.redo()
            EditorAction.STEP_FRAME_BACK -> viewModel.stepFrame(-1)
            EditorAction.STEP_FRAME_FORWARD -> viewModel.stepFrame(1)
            EditorAction.JUMP_START -> viewModel.jumpToStart()
            EditorAction.JUMP_END -> viewModel.jumpToEnd()
            EditorAction.ZOOM_IN -> viewModel.setTimelineZoom(viewModel.timelineZoom.value + 0.2f)
            EditorAction.ZOOM_OUT -> viewModel.setTimelineZoom(viewModel.timelineZoom.value - 0.2f)
        }
        return true
    }
}
