package com.varuna.openfuel.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpanishNumbersTest {
    @Test fun `decimal comma`() = assertEquals(1.879, SpanishNumbers.parseOrNull("1,879")!!, 1e-12)

    @Test fun `negative coordinate`() = assertEquals(-8.024778, SpanishNumbers.parseOrNull("-8,024778")!!, 1e-12)

    @Test fun `zero`() = assertEquals(0.0, SpanishNumbers.parseOrNull("0,0")!!, 0.0)

    @Test fun `empty means not sold`() = assertNull(SpanishNumbers.parseOrNull(""))

    @Test fun `blank and null`() {
        assertNull(SpanishNumbers.parseOrNull("   "))
        assertNull(SpanishNumbers.parseOrNull(null))
    }

    @Test fun `garbage is null, not zero`() = assertNull(SpanishNumbers.parseOrNull("n/d"))
}
