package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NumericValuesTest {

    @Test
    public void numbersAreReadBackFromTheDisplayedText() {
        assertEquals(1000.0, NumericValues.parse("1.000,00 EUR"), 0.001);
        assertEquals(1000.5, NumericValues.parse("1,000.50"), 0.001);
        assertEquals(-42.0, NumericValues.parse("-42"), 0.001);
        assertEquals(0.75, NumericValues.parse("0,75"), 0.001);
        assertTrue(Double.isNaN(NumericValues.parse("no number")));
        assertTrue(Double.isNaN(NumericValues.parse(null)));
    }

    @Test
    public void missingValuesComeBeforeEveryNumber() {
        assertTrue(NumericValues.compare("", "1") < 0);
        assertEquals(0, NumericValues.compare("", ""));
    }
}
