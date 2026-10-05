package com.jagajaga.calendarmap

/**
 * Picks which selected events to attend, and in what order, given travel times.
 *
 * Rules: you can arrive early and wait; at each event you must stay
 * `min(minStay, event length)` (or the whole event when `minStayMs` is null),
 * and that stay must fit before the event ends. The best plan attends the
 * most events, then needs the least travel, then finishes earliest.
 */
object RoutePlanner {
    const val MAX_STOPS = 10

    data class Stop(val begin: Long, val end: Long)

    data class Visit(
        val stop: Int,
        /** When you get there (may be before the event starts). */
        val arrive: Long,
        /** When your stay starts: max(arrive, begin). */
        val stayFrom: Long,
        /** Latest you can leave and still make the rest of the plan. */
        val leaveBy: Long,
        /** Travel time of the leg that brought you here. */
        val travelMs: Long,
    )

    /**
     * Free time with nothing to attend and no need to be travelling.
     *
     * @param beforeVisit index into [Plan.visits] of the stop you'll head to next.
     *   `0` with [Plan.departAt] set means "before leaving your start point".
     * @param from when you're free: the previous event's end (or now, at the start).
     * @param until the latest you can leave and still arrive as the next event starts.
     */
    data class Gap(val beforeVisit: Int, val from: Long, val until: Long) {
        val lengthMs: Long get() = until - from
    }

    data class Plan(
        val visits: List<Visit>,
        val missed: List<Int>,
        val totalTravelMs: Long,
        /** When to leave the start point, if there is one. */
        val departAt: Long?,
        /** Positive free windows, in route order. */
        val gaps: List<Gap> = emptyList(),
    ) {
        /** Free time between events, not counting the wait before setting off. */
        val freeBetweenMs: Long get() = gaps.filter { it.beforeVisit > 0 }.sumOf { it.lengthMs }
    }

    /**
     * @param travelMs travel time between nodes; nodes `0 until stops.size` are
     *   the stops, and node `stops.size` is the start point when [hasStart].
     * @param now earliest moment you can leave the start point.
     */
    fun plan(
        stops: List<Stop>,
        travelMs: Array<LongArray>,
        hasStart: Boolean,
        minStayMs: Long?,
        now: Long,
    ): Plan {
        val n = stops.size
        require(n <= MAX_STOPS) { "At most $MAX_STOPS stops" }
        if (n == 0) return Plan(emptyList(), emptyList(), 0, null)
        val required = LongArray(n) { i ->
            val len = stops[i].end - stops[i].begin
            if (minStayMs == null) len else minOf(minStayMs, len)
        }

        var bestOrder = IntArray(0)
        var bestTravel = Long.MAX_VALUE
        var bestFinish = Long.MAX_VALUE
        val order = IntArray(n)
        // Dominance memo: (visited set, last stop) -> (time, travel) pairs already explored.
        val seen = HashMap<Long, MutableList<LongArray>>()

        fun better(count: Int, travel: Long, finish: Long): Boolean = when {
            count != bestOrder.size -> count > bestOrder.size
            travel != bestTravel -> travel < bestTravel
            else -> finish < bestFinish
        }

        /** `last == -1` means "nowhere yet"; `last == n` means the start point. */
        fun dfs(mask: Int, last: Int, depth: Int, time: Long, travel: Long) {
            if (depth > 0 && better(depth, travel, time)) {
                bestOrder = order.copyOf(depth)
                bestTravel = travel
                bestFinish = time
            }
            val key = (mask.toLong() shl 8) or (last + 1).toLong()
            val explored = seen.getOrPut(key) { mutableListOf() }
            if (explored.any { it[0] <= time && it[1] <= travel }) return
            explored += longArrayOf(time, travel)

            for (next in 0 until n) {
                if (mask and (1 shl next) != 0) continue
                val leg = if (last < 0) 0L else travelMs[last][next]
                val arrive = if (last < 0) stops[next].begin else time + leg
                val stayFrom = maxOf(arrive, stops[next].begin)
                val leave = stayFrom + required[next]
                if (leave > stops[next].end) continue
                order[depth] = next
                dfs(mask or (1 shl next), next, depth + 1, leave, travel + leg)
            }
        }

        if (hasStart) dfs(0, n, 0, now, 0) else dfs(0, -1, 0, Long.MIN_VALUE, 0)
        return buildPlan(bestOrder, stops, travelMs, hasStart, required, now)
    }

    private fun buildPlan(
        order: IntArray,
        stops: List<Stop>,
        travelMs: Array<LongArray>,
        hasStart: Boolean,
        required: LongArray,
        now: Long,
    ): Plan {
        val n = stops.size
        val missed = (0 until n).filter { it !in order }
        if (order.isEmpty()) return Plan(emptyList(), missed, 0, null)

        val legs = LongArray(order.size) { k ->
            when {
                k > 0 -> travelMs[order[k - 1]][order[k]]
                hasStart -> travelMs[n][order[0]]
                else -> 0L
            }
        }
        // Forward pass: earliest arrival at each stop.
        val arrive = LongArray(order.size)
        val stayFrom = LongArray(order.size)
        var t = now
        for (k in order.indices) {
            val s = stops[order[k]]
            arrive[k] = if (k == 0 && !hasStart) s.begin else t + legs[k]
            stayFrom[k] = maxOf(arrive[k], s.begin)
            t = stayFrom[k] + required[order[k]]
        }
        // Backward pass: latest you may leave each stop and still make the rest.
        val leaveBy = LongArray(order.size)
        for (k in order.indices.reversed()) {
            val end = stops[order[k]].end
            leaveBy[k] = if (k == order.size - 1) end else {
                val nextLatestStayStart = leaveBy[k + 1] - required[order[k + 1]]
                minOf(end, nextLatestStayStart - legs[k + 1])
            }
        }
        // Leave the start point so you arrive as the first event begins, but not before now.
        val departAt = if (hasStart) {
            val firstStop = stops[order[0]]
            val latestStayStart = leaveBy[0] - required[order[0]]
            maxOf(now, minOf(firstStop.begin, latestStayStart) - legs[0])
        } else null

        val visits = order.indices.map { k -> Visit(order[k], arrive[k], stayFrom[k], leaveBy[k], legs[k]) }

        // Free windows: from when one event ends until you must set off to arrive
        // as the next one begins. If you'd have to leave before the end, there's none.
        val gaps = mutableListOf<Gap>()
        if (departAt != null && departAt > now) gaps += Gap(0, now, departAt)
        for (k in 1 until order.size) {
            val next = stops[order[k]]
            val latestStayStart = leaveBy[k] - required[order[k]]
            val setOff = minOf(next.begin, latestStayStart) - legs[k]
            val prevEnd = stops[order[k - 1]].end
            if (setOff > prevEnd) gaps += Gap(k, prevEnd, setOff)
        }
        return Plan(visits, missed, legs.sum(), departAt, gaps)
    }
}
