package com.dyzyks.montager.model

class UndoRedoManager(private val maxHistorySize: Int = 50) {

    private val undoStack = mutableListOf<Project>()
    private val redoStack = mutableListOf<Project>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    /**
     * Pushes the current state to the undo stack before applying a modification.
     * Clears the redo stack.
     */
    fun pushState(currentState: Project) {
        undoStack.add(currentState)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    /**
     * Performs undo: saves [currentState] onto redoStack, pops the last state from undoStack.
     * Returns the previous project state, or null if cannot undo.
     */
    fun undo(currentState: Project): Project? {
        if (!canUndo) return null
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentState)
        if (redoStack.size > maxHistorySize) {
            redoStack.removeAt(0)
        }
        return previousState
    }

    /**
     * Performs redo: saves [currentState] onto undoStack, pops the forward state from redoStack.
     * Returns the redone project state, or null if cannot redo.
     */
    fun redo(currentState: Project): Project? {
        if (!canRedo) return null
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(currentState)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        return nextState
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
