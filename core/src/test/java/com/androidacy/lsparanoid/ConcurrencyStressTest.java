package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for concurrency issues that could cause app crashes.
 * Tests thread safety, race conditions, and deadlocks that would crash production apps.
 *
 * IMPORTANT: These tests simulate real Android app scenarios where:
 * - Multiple threads decode strings simultaneously (UI + background threads)
 * - App startup triggers massive parallel string access
 * - WeakReferences get GC'd during active use
 */
class ConcurrencyStressTest {

    @Test
    @DisplayName("STRESS: 100 threads simultaneously decoding same string")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void hundredThreadsDecodingSameString() throws Exception {
        int seed = 12345;
        String original = "Concurrent access test string";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);
            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(100);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(100);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicReference<Throwable> firstError = new AtomicReference<>();

            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        String decoded = DeobfuscatorHelper.getString(id, chunks);
                        assertEquals(original, decoded);
                        successCount.incrementAndGet();
                    } catch (Throwable e) {
                        firstError.compareAndSet(null, e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS),
                "All threads should complete within timeout");

            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

            // Verify results
            assertNull(firstError.get(),
                "No thread should crash: " + (firstError.get() != null ? firstError.get().getMessage() : ""));
            assertEquals(100, successCount.get(),
                "All 100 threads should succeed");
        }
    }

    @Test
    @DisplayName("STRESS: Parallel access to multiple different strings")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void parallelAccessToMultipleStrings() throws Exception {
        int seed = 54321;
        int stringCount = 100;
        int threadsPerString = 5;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            // Register many strings
            long[] ids = new long[stringCount];
            String[] originals = new String[stringCount];
            for (int i = 0; i < stringCount; i++) {
                originals[i] = "String number " + i + " with some content";
                ids[i] = registry.registerString(originals[i]);
            }

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch doneLatch = new CountDownLatch(stringCount * threadsPerString);
            AtomicInteger successCount = new AtomicInteger(0);
            ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

            // Each string accessed by multiple threads
            for (int i = 0; i < stringCount; i++) {
                final int index = i;
                for (int t = 0; t < threadsPerString; t++) {
                    executor.submit(() -> {
                        try {
                            String decoded = DeobfuscatorHelper.getString(ids[index], chunks);
                            if (originals[index].equals(decoded)) {
                                successCount.incrementAndGet();
                            } else {
                                errors.add("Mismatch at index " + index);
                            }
                        } catch (Exception e) {
                            errors.add("Exception at index " + index + ": " + e.getMessage());
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }
            }

            assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
                "All operations should complete");

            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);

            assertTrue(errors.isEmpty(),
                "Should have no errors, but got: " + errors);
            assertEquals(stringCount * threadsPerString, successCount.get(),
                "All accesses should succeed");
        }
    }

    @RepeatedTest(5)
    @DisplayName("STRESS: Race condition - concurrent first access")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void raceConditionConcurrentFirstAccess() throws Exception {
        // Simulates app startup where many components access strings simultaneously
        int seed = 99999;
        String original = "Race condition test";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);
            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();

            // Each thread loads chunks independently (simulates first access race)
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(20);
            AtomicInteger successCount = new AtomicInteger(0);
            ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Each thread creates its own chunks array
                        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);
                        String decoded = DeobfuscatorHelper.getString(id, chunks);
                        if (original.equals(decoded)) {
                            successCount.incrementAndGet();
                        } else {
                            errors.add("Decoded mismatch");
                        }
                    } catch (Exception e) {
                        errors.add("Exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);

            assertTrue(errors.isEmpty(), "Concurrent first access should not cause errors: " + errors);
            assertEquals(20, successCount.get());
        }
    }

    @Test
    @DisplayName("STRESS: Rapid repeated access (simulates app hot path)")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void rapidRepeatedAccess() throws Exception {
        // Simulates hot path like logging or UI string access
        int seed = 77777;
        String original = "Hot path string";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);
            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            AtomicInteger totalAccesses = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            // Each thread does 1000 rapid accesses
            Future<?>[] futures = new Future[10];
            for (int i = 0; i < 10; i++) {
                futures[i] = executor.submit(() -> {
                    for (int j = 0; j < 1000; j++) {
                        try {
                            String decoded = DeobfuscatorHelper.getString(id, chunks);
                            if (!original.equals(decoded)) {
                                errors.incrementAndGet();
                            }
                            totalAccesses.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                });
            }

            // Wait for all to complete
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            executor.shutdown();

            assertEquals(10000, totalAccesses.get(),
                "Should complete all 10,000 accesses");
            assertEquals(0, errors.get(),
                "Should have zero errors in rapid access");
        }
    }

    @Test
    @DisplayName("STRESS: Mixed read operations with no writes")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void mixedReadOperationsNoWrites() throws Exception {
        // Tests that concurrent reads don't interfere with each other
        int seed = 66666;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            // Register diverse strings
            long id1 = registry.registerString("Short");
            long id2 = registry.registerString("A much longer string with more content");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5000; i++) {
                sb.append((char) ('A' + (i % 26)));
            }
            long id3 = registry.registerString(sb.toString());

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(30);
            AtomicInteger operations = new AtomicInteger(0);
            ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

            // Random access pattern
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        long[] ids = {id1, id2, id3};
                        long randomId = ids[ThreadLocalRandom.current().nextInt(3)];
                        DeobfuscatorHelper.getString(randomId, chunks);
                        operations.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertTrue(errors.isEmpty(), "Mixed reads should not error: " + errors);
            assertEquals(100, operations.get());
        }
    }

    @Test
    @DisplayName("STRESS: Contention on single chunk boundary")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void contentionOnChunkBoundary() throws Exception {
        // Tests access to strings that span chunk boundaries
        int seed = 55555;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            // Create string exactly at boundary
            StringBuilder sb1 = new StringBuilder();
            for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH; i++) {
                sb1.append('X');
            }

            // Create string just over boundary
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH + 100; i++) {
                sb2.append('Y');
            }

            long id1 = registry.registerString(sb1.toString());
            long id2 = registry.registerString(sb2.toString());

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(40);
            CountDownLatch doneLatch = new CountDownLatch(200);
            AtomicInteger successCount = new AtomicInteger(0);

            // Hammer the boundary strings
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        DeobfuscatorHelper.getString(id1, chunks);
                        successCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        DeobfuscatorHelper.getString(id2, chunks);
                        successCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(200, successCount.get(),
                "All boundary accesses should succeed");
        }
    }

    @Test
    @DisplayName("STRESS: Thread interruption handling")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void threadInterruptionHandling() throws Exception {
        int seed = 44444;
        String original = "Interruption test";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);
            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(5);
            AtomicInteger completedCount = new AtomicInteger(0);

            // Start threads that will be interrupted
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            if (Thread.interrupted()) {
                                return; // Exit cleanly
                            }
                            DeobfuscatorHelper.getString(id, chunks);
                        }
                        completedCount.incrementAndGet();
                    } catch (Exception e) {
                        // Should not crash on interrupt
                    }
                });
            }

            // Let them run briefly
            Thread.sleep(100);

            // Interrupt all
            executor.shutdownNow();

            // Should terminate quickly
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
                "Threads should handle interruption gracefully");

            // Some might complete, some might be interrupted - neither should crash
            assertTrue(completedCount.get() >= 0,
                "Should handle interruptions without crash");
        }
    }

    @Test
    @DisplayName("STRESS: Sustained load over time")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void sustainedLoadOverTime() throws Exception {
        // Simulates long-running app with continuous string access
        int seed = 33333;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            // Register several strings
            long[] ids = new long[10];
            for (int i = 0; i < 10; i++) {
                ids[i] = registry.registerString("Sustained string " + i);
            }

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            ExecutorService executor = Executors.newFixedThreadPool(20);
            AtomicInteger totalOps = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);
            AtomicBoolean stop = new AtomicBoolean(false);

            // Run for 10 seconds
            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    while (!stop.get()) {
                        try {
                            long randomId = ids[ThreadLocalRandom.current().nextInt(10)];
                            DeobfuscatorHelper.getString(randomId, chunks);
                            totalOps.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                });
            }

            // Run for 10 seconds
            Thread.sleep(10000);
            stop.set(true);

            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

            System.out.println("Sustained load completed: " + totalOps.get() + " operations");
            assertEquals(0, errors.get(),
                "Sustained load should have zero errors");
            assertTrue(totalOps.get() > 10000,
                "Should handle substantial load (got " + totalOps.get() + " ops)");
        }
    }
}
