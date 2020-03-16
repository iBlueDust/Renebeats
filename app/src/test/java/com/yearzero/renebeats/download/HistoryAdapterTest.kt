package com.yearzero.renebeats.download

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max

class HistoryAdapterTest {
	@Test
	fun testItemTypePair() {
		val position = 4
//        if (position < 0) throw ArrayIndexOutOfBoundsException(position)
		var current = 0
		var i = 0
		val sections = arrayOf(2, 1)
		while (sections[i] + current < position) {
			current += max(0, sections[i++]) + 1
		}

		if (position == current) {
			Pair(-1, i)
			assertEquals(i, 1)
		} else {
			Pair(i, position - current - 1)
			assertEquals(i, 1)
			assertEquals(position - current - 1, 0)
		}
	}
}