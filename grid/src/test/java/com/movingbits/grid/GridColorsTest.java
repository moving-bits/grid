package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Checks that an explicitly set color wins. What is left unset is read from the theme, and
 * that takes an Android view – out of reach of a JVM test.
 */
public class GridColorsTest {

    @Test
    public void everyColorThatIsSetIsHandedOutUnchanged() {
        GridColors colors = new GridColors()
                .surface(0xFF102030)
                .onSurface(0xFF405060)
                .accent(0xFF708090)
                .alternateRow(0xFFA0B0C0)
                .readOnlyHeader(0xFFD0E0F0)
                .divider(0x22000000)
                .columnDivider(0x44000000)
                .boundaryShadow(0x66000000)
                .scrollHint(0x88000000);

        // The view is only consulted when nothing was set, so null is enough here.
        assertEquals(0xFF102030, colors.surface(null));
        assertEquals(0xFF405060, colors.onSurface(null));
        assertEquals(0xFF708090, colors.accent(null));
        assertEquals(0xFFA0B0C0, colors.alternateRow(null));
        assertEquals(0xFFD0E0F0, colors.readOnlyHeader(null));
        assertEquals(0x22000000, colors.divider(null));
        assertEquals(0x44000000, colors.columnDivider(null));
        assertEquals(0x66000000, colors.boundaryShadow(null));
        assertEquals(0x88000000, colors.scrollHint(null));
    }

    @Test
    public void aFullyTransparentColorCountsAsSet() {
        assertEquals(0, new GridColors().surface(0).surface(null));
    }

    @Test
    public void withoutAnOnSurfaceColorTheTextAppearanceKeepsItsOwn() {
        assertNull(new GridColors().text());
        assertEquals(Integer.valueOf(0xFF405060), new GridColors().onSurface(0xFF405060).text());
    }

    @Test
    public void aGridStartsOnTheThemeAndTakesOverAPalette() {
        Grid grid = new Grid();
        assertNotNull(grid.getColors());
        assertNull(grid.getColors().text());

        GridColors palette = new GridColors().onSurface(0xFF123456);
        assertSame(palette, grid.colors(palette).getColors());

        // null goes back to the theme instead of leaving the grid without colors at all.
        assertNotNull(grid.colors(null).getColors());
        assertNull(grid.getColors().text());
    }
}
