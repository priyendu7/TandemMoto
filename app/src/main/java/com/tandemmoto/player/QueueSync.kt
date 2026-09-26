package com.tandemmoto.player

/** One step that edits the player's queue in place (#51). */
sealed interface QueueStep {
    data class Remove(val index: Int) : QueueStep

    data class Add(val index: Int, val id: String) : QueueStep

    data class Move(val from: Int, val to: Int) : QueueStep
}

/**
 * The steps from the player's queue to the ride playlist's order, applied one by one, so the
 * player keeps playing the current song through edits from either phone instead of restarting
 * (which a whole new queue would do).
 */
object QueueSync {
    fun steps(queue: List<String>, target: List<String>): List<QueueStep> {
        val steps = mutableListOf<QueueStep>()
        val now = queue.toMutableList()
        val wanted = target.toSet()
        // Removals from the end, so earlier indexes stay valid.
        for (i in now.indices.reversed()) {
            if (now[i] !in wanted) {
                steps += QueueStep.Remove(i)
                now.removeAt(i)
            }
        }
        for ((i, id) in target.withIndex()) {
            if (now.getOrNull(i) == id) continue
            val at = now.indexOf(id)
            if (at >= 0) {
                steps += QueueStep.Move(at, i)
                now.add(i, now.removeAt(at))
            } else {
                steps += QueueStep.Add(i, id)
                now.add(i, id)
            }
        }
        return steps
    }

    /** [queue] with [steps] applied, as the player would end up. */
    fun apply(queue: List<String>, steps: List<QueueStep>): List<String> {
        val now = queue.toMutableList()
        for (step in steps) {
            when (step) {
                is QueueStep.Remove -> now.removeAt(step.index)
                is QueueStep.Add -> now.add(step.index, step.id)
                is QueueStep.Move -> now.add(step.to, now.removeAt(step.from))
            }
        }
        return now
    }
}
