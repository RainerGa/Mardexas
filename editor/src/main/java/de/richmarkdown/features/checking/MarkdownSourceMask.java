package de.richmarkdown.features.checking;

/** Keeps source offsets intact while removing code and escaped punctuation from checks. */
public final class MarkdownSourceMask {
    private MarkdownSourceMask() {}
    public static String prose(String source) {
        char[] result = source.toCharArray(); int start = 0; char fence = 0; int fenceLength = 0;
        while (start < source.length()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int end = source.indexOf('\n', start); if (end < 0) end = source.length();
            int at = start; while (at < end && at - start < 4 && source.charAt(at) == ' ') at++;
            char marker = at < end ? source.charAt(at) : 0; int run = at;
            while (run < end && source.charAt(run) == marker) run++;
            int count = run - at;
            if (fence != 0) { blank(result, start, end); if (marker == fence && count >= fenceLength && source.substring(run, end).isBlank()) fence = 0; }
            else if (at - start <= 3 && (marker == '`' || marker == '~') && count >= 3) { fence = marker; fenceLength = count; blank(result, start, end); }
            else if (at - start >= 4 || marker == '\t') blank(result, start, end);
            start = end + 1;
        }
        // Remove escaped punctuation before collecting code spans.
        for (int i = 0; i < result.length; i++) {
            if ((i & 1023) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (result[i] == '\\' && i + 1 < result.length && !Character.isLetterOrDigit(result[i + 1]) && result[i + 1] != '\n') { result[i] = ' '; result[++i] = ' '; }
        }
        // Index the next run of each length once; unmatched runs must not rescan the entire tail.
        java.util.List<int[]> runs = new java.util.ArrayList<>();
        for (int i = 0; i < result.length; i++) {
            if (result[i] != '`') continue;
            int count = 1; while (i + count < result.length && result[i + count] == '`') count++;
            runs.add(new int[]{i, count, -1}); i += count - 1;
        }
        java.util.Map<Integer, Integer> next = new java.util.HashMap<>();
        for (int i = runs.size() - 1; i >= 0; i--) { int[] run = runs.get(i); Integer following = next.put(run[1], i); if (following != null) run[2] = following; }
        for (int i = 0; i < runs.size(); i++) {
            if ((i & 1023) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int[] run = runs.get(i);
            if (run[2] >= 0) { int[] close = runs.get(run[2]); blank(result, run[0], close[0] + close[1]); i = run[2]; }
        }
        return new String(result);
    }
    private static void blank(char[] chars, int start, int end) { for (int i = start; i < end; i++) if (chars[i] != '\n' && chars[i] != '\r') chars[i] = ' '; }
}
