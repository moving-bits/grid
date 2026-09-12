package com.movingbits.grid;

/**
 * One condition of a {@code WHERE} or {@code HAVING} clause: either a comparison or a group of
 * conditions. The set is closed; there is no implementation outside the library.
 */
public interface SqlCondition {

    /** {@code true} when an aggregate takes part in the condition. */
    boolean hasAggregate();

    /** {@code true} when nothing is compared - an empty group, for instance. */
    boolean isEmpty();

    /** {@code true} when every part needed is in place. */
    boolean isComplete();

    /** Short text for the chip in the editor. */
    String label();
}
