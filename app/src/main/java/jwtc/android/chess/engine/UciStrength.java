package jwtc.android.chess.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strength capabilities advertised during a UCI handshake. */
public final class UciStrength {
    private static final Pattern ELO =
            Pattern.compile(
                    "option name UCI_Elo type spin default (-?\\d+) min (-?\\d+) max (-?\\d+)");
    private boolean limit;
    private Integer minimum;
    private int maximum;
    private int defaultElo;
    private boolean configured;
    private Integer appliedElo;

    public void accept(String line) {
        line = line.trim().replaceAll("\\s+", " ");
        if (line.matches("option name UCI_LimitStrength type check default (true|false)")) {
            limit = true;
        }
        Matcher match = ELO.matcher(line);
        if (match.matches()) {
            try {
                int value = Integer.parseInt(match.group(1));
                int min = Integer.parseInt(match.group(2));
                int max = Integer.parseInt(match.group(3));
                if (min <= value && value <= max) {
                    minimum = min;
                    maximum = max;
                    defaultElo = value;
                }
            } catch (NumberFormatException ignored) {
                // Ignore ranges that cannot be represented by UCI integer settings.
            }
        }
    }

    public boolean supported() {
        return limit && minimum != null;
    }

    /** Commands needed before the next search; null requests unrestricted play or analysis. */
    List<String> configure(Integer targetElo) {
        List<String> commands = new ArrayList<>();
        Integer desired = targetElo != null && supported() ? clamp(targetElo) : null;
        if (limit && (!configured || !Objects.equals(appliedElo, desired))) {
            commands.add("setoption name UCI_LimitStrength value " + (desired != null));
            if (desired != null) {
                commands.add("setoption name UCI_Elo value " + desired);
            }
            configured = true;
            appliedElo = desired;
        }
        return commands;
    }

    public int defaultElo() {
        return defaultElo;
    }

    public int clamp(int elo) {
        return Math.max(minimum, Math.min(maximum, elo));
    }

    /** Include endpoints and the saved value, with roughly 100-point steps for normal ranges. */
    public List<Integer> choices(int savedElo) {
        TreeSet<Integer> values = new TreeSet<>();
        values.add(minimum);
        values.add(maximum);
        values.add(clamp(savedElo));
        long step = Math.max(100, (((long) maximum - minimum) / 10000 + 1) * 100);
        for (long value = Math.floorDiv((long) minimum, step) * step + step;
                value < maximum;
                value += step) {
            values.add((int) value);
        }
        return new ArrayList<>(values);
    }
}
