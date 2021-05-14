package io.gnosis.safe.utils

import org.junit.Assert.*
import org.junit.Test

class SemVerTest {

    @Test
    fun `parse (versionString) should return SemVer`() {
        val version1 = SemVer.parse("2.15.0")
        assertEquals(SemVer(2, 15, 0), version1)

        val version2 = SemVer.parse("2.15.0-255-internal")
        assertEquals(SemVer(2, 15, 0, "255-internal"), version2)

        val version3 = SemVer.parse("2.15.0-255rc-internal")
        assertEquals(SemVer(2, 15, 0, "255rc-internal"), version3)
    }

    @Test
    fun `parse (versionString, ignoreExtensions) should return SemVer`() {
        val version1 = SemVer.parse("2.15.0", true)
        assertEquals(SemVer(2, 15, 0), version1)

        val version2 = SemVer.parse("2.15.0-255-internal", true)
        assertNotEquals(SemVer(2, 15, 0, "255-internal"), version2)
        assertEquals(SemVer(2, 15, 0), version2)

        val version3 = SemVer.parse("2.15.0-255rc-internal", true)
        assertNotEquals(SemVer(2, 15, 0, "255rc-internal"), version3)
        assertEquals(SemVer(2, 15, 0), version3)
    }

    @Test
    fun `parseRange (rangeString) should return pair of SemVer`() {
        val version1 = SemVer.parse("2.15.0")
        val version2 = SemVer.parse("2.17.0")
        val version3 = SemVer.parse("2.15.0-255-internal")
        val version4 = SemVer.parse("2.17.0-300-internal")
        
        val rangeString1 = "2.15.0...2.17.0"
        val range1 = SemVer.parseRange(rangeString1)
        assert(range1.first != null)
        assert(range1.second != null)
        assertEquals(version1, range1.first)
        assertEquals(version2, range1.second)

        val rangeString2 = "2.15.0"
        val range2 = SemVer.parseRange(rangeString2)
        assert(range2.first != null)
        assert(range2.second == null)
        assertEquals(version1, range1.first)

        val rangeString3 = "2.15.0-255-internal"
        val range3 = SemVer.parseRange(rangeString3)
        assert(range3.first != null)
        assert(range3.second == null)
        assertEquals(version3, range3.first)

        val rangeString4 = "2.15.0-255-internal...2.17.0-300-internal"
        val range4 = SemVer.parseRange(rangeString4)
        assert(range4.first != null)
        assert(range4.second != null)
        assertEquals(version3, range4.first)
        assertEquals(version4, range4.second)
    }

    @Test
    fun `parseRange (rangeString, ignoreExtensions) should return pair of SemVer`() {
        val version1 = SemVer.parse("2.15.0")
        val version2 = SemVer.parse("2.17.0")
        val version3 = SemVer.parse("2.15.0-255-internal")
        val version4 = SemVer.parse("2.17.0-300-internal")

        val rangeString1 = "2.15.0...2.17.0"
        val range1 = SemVer.parseRange(rangeString1, true)
        assert(range1.first != null)
        assert(range1.second != null)
        assertEquals(version1, range1.first)
        assertEquals(version2, range1.second)

        val rangeString2 = "2.15.0"
        val range2 = SemVer.parseRange(rangeString2, true)
        assert(range2.first != null)
        assert(range2.second == null)
        assertEquals(version1, range1.first)

        val rangeString3 = "2.15.0-255-internal"
        val range3 = SemVer.parseRange(rangeString3, true)
        assert(range3.first != null)
        assert(range3.second == null)
        assertNotEquals(version3, range3.first)
        assertEquals(SemVer(2, 15, 0), range3.first)

        val rangeString4 = "2.15.0-255-internal...2.17.0-300-internal"
        val range4 = SemVer.parseRange(rangeString4, true)
        assert(range4.first != null)
        assert(range4.second != null)
        assertNotEquals(version3, range4.first)
        assertEquals(SemVer(2, 15, 0), range4.first)
        assertNotEquals(version4, range4.second)
        assertEquals(SemVer(2, 17, 0), range4.second)
    }

    @Test
    fun `isInside (rangeList) should return if SemVer is inside the range list`() {
        val version1 = SemVer.parse("2.15.0")
        val version2 = SemVer.parse("2.17.0")
        val version3 = SemVer.parse("2.15.0-255-internal")
        val version4 = SemVer.parse("2.15.0-257-internal")

        val rangeList1 = "2.15.0"
        assert(version1.isInside(rangeList1))
        assertFalse(version2.isInside(rangeList1))

        val rangeList2 = "2.15.0...2.17.0"
        assert(version1.isInside(rangeList2))
        assert(version2.isInside(rangeList2))

        val rangeList3 = "2.11.0,2.12...2.13.0,2.14...2.16.0"
        assert(version1.isInside(rangeList3))
        assertFalse(version2.isInside(rangeList3))

        val rangeList4 = ""
        assertFalse(version1.isInside(rangeList4))

        val rangeList5 = "2.15.0-255-internal"
        assertFalse(version1.isInside(rangeList5))
        assert(version3.isInside(rangeList5))

        val rangeList6 = "2.15.0-255-internal...2.15.0-258-internal"
        assert(version4.isInside(rangeList6))
    }

    @Test
    fun `isInside (rangeList, ignoreExtensions) should return if SemVer is inside the range list`() {
        val version1 = SemVer.parse("2.15.0")
        val version2 = SemVer.parse("2.17.0")
        val version3 = SemVer.parse("2.15.0-255-internal")
        val version4 = SemVer.parse("2.15.0-257-internal")

        val rangeList1 = "2.15.0"
        assert(version1.isInside(rangeList1, true))
        assertFalse(version2.isInside(rangeList1, true))

        val rangeList2 = "2.15.0...2.17.0"
        assert(version1.isInside(rangeList2, true))
        assert(version2.isInside(rangeList2, true))

        val rangeList3 = "2.11.0,2.12...2.13.0,2.14...2.16.0"
        assert(version1.isInside(rangeList3, true))
        assertFalse(version2.isInside(rangeList3, true))

        val rangeList4 = ""
        assertFalse(version1.isInside(rangeList4, true))

        val rangeList5 = "2.15.0-255-internal"
        assert(version1.isInside(rangeList5, true))
        assert(version3.isInside(rangeList5, true))

        val rangeList6 = "2.15.0-255-internal...2.15.0-258-internal"
        assert(version1.isInside(rangeList5, true))
        assert(version3.isInside(rangeList5, true))
        assert(version4.isInside(rangeList6, true))
    }
}
