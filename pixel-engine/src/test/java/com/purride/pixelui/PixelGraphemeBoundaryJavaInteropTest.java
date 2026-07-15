package com.purride.pixelui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Verifies that the public grapheme kernel remains directly consumable from Java. */
public final class PixelGraphemeBoundaryJavaInteropTest {
    /** Java callers observe the same UTF-16 navigation, expansion, and version contracts. */
    @Test
    public void publicBoundaryApiHasStableJavaShape() {
        /** Decomposed accent plus ASCII suffix gives one interior UTF-16 offset. */
        final PixelGraphemeBoundaryMap map = new PixelGraphemeBoundaryMap("e\u0301x");
        /** Expanded selection returned as an explicit half-open UTF-16 range value. */
        final PixelUtf16Range range = map.expand(1, 1);

        assertEquals("17.0.0", PixelGraphemeBoundaryMap.UnicodeVersion);
        assertEquals("e\u0301x", map.getText());
        assertEquals(3, map.getUtf16Length());
        assertEquals(2, map.getGraphemeCount());
        assertTrue(map.isBoundary(0));
        assertFalse(map.isBoundary(1));
        assertEquals(0, map.floor(1));
        assertEquals(2, map.ceil(1));
        assertEquals(2, map.nearest(1));
        assertEquals(0, map.previous(2));
        assertEquals(2, map.next(0));
        assertEquals(2, range.getStart());
        assertEquals(2, range.getEnd());
        assertEquals(0, range.getLength());
        assertTrue(range.isCollapsed());
    }
}
