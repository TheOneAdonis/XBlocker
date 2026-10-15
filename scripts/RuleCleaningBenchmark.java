import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/** Local JVM microbenchmark; this does not measure Android UI frames or battery use.
 * Usage: java -cp <kotlin-stdlib.jar> scripts/RuleCleaningBenchmark.java <before.jar> <after.jar>
 */
class RuleCleaningBenchmark {
    private static volatile String sink;
    private static final com.sun.management.ThreadMXBean allocations =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static final class Cleaner implements AutoCloseable {
        final URLClassLoader loader;
        final Object parser;
        final Method clean;
        final boolean bounded;
        final Object[] arguments;

        Cleaner(String jar, boolean bounded) throws Exception {
            this.bounded = bounded;
            loader = new URLClassLoader(new URL[]{Path.of(jar).toUri().toURL()}, RuleCleaningBenchmark.class.getClassLoader());
            var type = loader.loadClass("io.github.xblocker.core.RuleParser");
            parser = type.getField("INSTANCE").get(null);
            clean = bounded ? type.getMethod("clean", String.class, int.class) : type.getMethod("clean", String.class);
            arguments = bounded ? new Object[]{"", 32768} : new Object[]{""};
        }

        String run() throws Exception {
            String result = (String) clean.invoke(parser, arguments);
            return bounded || result.length() <= 32768 ? result : result.substring(0, 32768);
        }

        public void close() throws Exception { loader.close(); }
    }

    private record Measurement(double microseconds, long allocatedBytes) {}

    private static Measurement measure(Cleaner cleaner, int iterations) throws Exception {
        long thread = Thread.currentThread().threadId();
        long bytes = allocations.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink = cleaner.run();
        long elapsed = System.nanoTime() - start;
        bytes = allocations.getThreadAllocatedBytes(thread) - bytes;
        return new Measurement(elapsed / (iterations * 1000.0), bytes / iterations);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected before.jar after.jar");
        if (!allocations.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counters unavailable");
        allocations.setThreadAllocatedMemoryEnabled(true);
        try (var before = new Cleaner(args[0], false); var after = new Cleaner(args[1], true)) {
            String[] names = {"short-multilingual", "long-ascii-1MiChars"};
            String[] inputs = {"Hello 世界😀 \u200B example.org ".repeat(12), "a".repeat(1_048_576)};
            int[] iterations = {2000, 8};
            System.out.println("scenario,implementation,median_us_per_op,median_allocated_bytes_per_op");
            for (int s = 0; s < inputs.length; s++) {
                before.arguments[0] = after.arguments[0] = inputs[s];
                if (!before.run().equals(after.run())) throw new AssertionError("Output changed: " + names[s]);
                for (int i = 0; i < (s == 0 ? 3000 : 16); i++) { sink = before.run(); sink = after.run(); }
                double[][] times = new double[2][5];
                long[][] bytes = new long[2][5];
                for (int round = 0; round < 5; round++) {
                    for (int step = 0; step < 2; step++) {
                        int implementation = (round + step) % 2;
                        var m = measure(implementation == 0 ? before : after, iterations[s]);
                        times[implementation][round] = m.microseconds();
                        bytes[implementation][round] = m.allocatedBytes();
                    }
                }
                for (int i = 0; i < 2; i++) {
                    Arrays.sort(times[i]); Arrays.sort(bytes[i]);
                    System.out.printf(java.util.Locale.ROOT, "%s,%s,%.3f,%d%n", names[s], i == 0 ? "before" : "after", times[i][2], bytes[i][2]);
                }
            }
        }
    }
}
