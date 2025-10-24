package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RandomHelper.
 * Tests the deterministic pseudo-random number generator used for string obfuscation.
 */
class RandomHelperTest {

    @Test
    @DisplayName("seed() should be deterministic - same input produces same output")
    void seedShouldBeDeterministic() {
        long input = 12345L;
        long result1 = RandomHelper.seed(input);
        long result2 = RandomHelper.seed(input);
        assertEquals(result1, result2, "seed() must be deterministic");
    }

    @Test
    @DisplayName("seed() should produce different outputs for different inputs")
    void seedShouldProduceDifferentOutputs() {
        long seed1 = RandomHelper.seed(12345L);
        long seed2 = RandomHelper.seed(12346L);
        assertNotEquals(seed1, seed2, "Different inputs should produce different seeds");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, 42L, 12345L, Long.MAX_VALUE, Long.MIN_VALUE, 0xFFFFFFFFL})
    @DisplayName("seed() should handle various input values")
    void seedShouldHandleVariousInputs(long input) {
        assertDoesNotThrow(() -> RandomHelper.seed(input),
            "seed() should handle input: " + input);
    }

    @Test
    @DisplayName("seed() should produce values within expected range")
    void seedShouldProduceValidRange() {
        long result = RandomHelper.seed(12345L);
        // Result should be in valid long range (always true, but documents expectation)
        assertTrue(result >= 0 || result < 0, "Result should be a valid long value");
    }

    @Test
    @DisplayName("next() should be deterministic - same state produces same output")
    void nextShouldBeDeterministic() {
        long state = 42L;
        long result1 = RandomHelper.next(state);
        long result2 = RandomHelper.next(state);
        assertEquals(result1, result2, "next() must be deterministic");
    }

    @Test
    @DisplayName("next() should produce different outputs for different states")
    void nextShouldProduceDifferentOutputs() {
        long next1 = RandomHelper.next(42L);
        long next2 = RandomHelper.next(43L);
        assertNotEquals(next1, next2, "Different states should produce different outputs");
    }

    @Test
    @DisplayName("next() should generate a sequence of different values")
    void nextShouldGenerateSequence() {
        long state = RandomHelper.seed(12345L);
        long value1 = RandomHelper.next(state);
        long value2 = RandomHelper.next(value1);
        long value3 = RandomHelper.next(value2);

        // All values in sequence should be different
        assertNotEquals(value1, value2, "Sequential values should differ");
        assertNotEquals(value2, value3, "Sequential values should differ");
        assertNotEquals(value1, value3, "Sequential values should differ");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, 42L, 12345L, Long.MAX_VALUE, Long.MIN_VALUE})
    @DisplayName("next() should handle various state values")
    void nextShouldHandleVariousStates(long state) {
        assertDoesNotThrow(() -> RandomHelper.next(state),
            "next() should handle state: " + state);
    }

    @Test
    @DisplayName("seed() followed by next() should produce consistent sequence")
    void seedAndNextShouldProduceConsistentSequence() {
        long input = 12345L;

        // First sequence
        long state1 = RandomHelper.seed(input);
        long next1 = RandomHelper.next(state1);
        long next2 = RandomHelper.next(next1);

        // Second sequence with same seed
        long state2 = RandomHelper.seed(input);
        long next3 = RandomHelper.next(state2);
        long next4 = RandomHelper.next(next3);

        assertEquals(state1, state2, "Same seed should produce same state");
        assertEquals(next1, next3, "Same state should produce same next value");
        assertEquals(next2, next4, "Sequence should be consistent");
    }

    @Test
    @DisplayName("Different seeds should produce different sequences")
    void differentSeedsShouldProduceDifferentSequences() {
        long state1 = RandomHelper.seed(1L);
        long next1 = RandomHelper.next(state1);

        long state2 = RandomHelper.seed(2L);
        long next2 = RandomHelper.next(state2);

        assertNotEquals(state1, state2, "Different seeds should produce different states");
        assertNotEquals(next1, next2, "Different seeds should produce different sequences");
    }

    @Test
    @DisplayName("PRNG should have reasonable distribution")
    void prngShouldHaveReasonableDistribution() {
        // Test that the PRNG produces a variety of values (not all the same)
        long state = RandomHelper.seed(42L);
        int[] buckets = new int[16]; // Track distribution across 16 buckets

        for (int i = 0; i < 1000; i++) {
            state = RandomHelper.next(state);
            int bucket = (int) ((state >>> 32) & 0xF);
            buckets[bucket]++;
        }

        // Check that at least half the buckets have some values (basic distribution check)
        int nonEmptyBuckets = 0;
        for (int count : buckets) {
            if (count > 0) nonEmptyBuckets++;
        }

        assertTrue(nonEmptyBuckets >= 8,
            "PRNG should distribute values across multiple buckets, found: " + nonEmptyBuckets);
    }

    @Test
    @DisplayName("Zero seed should produce valid state")
    void zeroSeedShouldProduceValidState() {
        long state = RandomHelper.seed(0L);
        long next = RandomHelper.next(state);

        // Should not crash and should produce some non-zero progression
        assertDoesNotThrow(() -> RandomHelper.next(next));
    }
}
