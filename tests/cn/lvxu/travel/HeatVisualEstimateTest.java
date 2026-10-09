package cn.lvxu.travel;
import java.util.Arrays;

public final class HeatVisualEstimateTest {
    static int count;
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); count++; }
    static int[] tile(int colour) { int[] out = new int[441]; Arrays.fill(out, colour); return out; }
    static HeatVisualEstimate.Level estimate(int[] heat, int[] base) {
        return HeatVisualEstimate.estimate(heat, base, 21, 21, 10, 10, 7);
    }
    public static void main(String[] args) {
        int[] base = tile(0xffeeeeee);
        check(estimate(tile(0xffd73838), base) == HeatVisualEstimate.Level.HIGH, "red high reference");
        check(estimate(tile(0xffdedf76), base) == HeatVisualEstimate.Level.MEDIUM, "yellow medium reference");
        check(estimate(tile(0xffcb853f), base) == HeatVisualEstimate.Level.MEDIUM, "orange medium reference");
        check(estimate(tile(0xff7de381), base) == HeatVisualEstimate.Level.LOW, "green low reference");
        check(estimate(tile(0xff7b79d1), base) == HeatVisualEstimate.Level.LOW, "purple low reference");
        check(estimate(tile(0xff7ddedd), base) == HeatVisualEstimate.Level.LOW, "cyan low reference");
        check(estimate(base, base) == HeatVisualEstimate.Level.UNKNOWN, "no heat unknown");
        int[] park = tile(0xff7de381);
        check(estimate(park, park) == HeatVisualEstimate.Level.UNKNOWN, "unchanged park unknown");
        check(estimate(tile(0xffd73838), park) == HeatVisualEstimate.Level.UNKNOWN, "saturated base cannot prove heat");
        int[] noise = base.clone();
        for (int i = 0; i < noise.length; i += 3) noise[i] = 0xffd73838;
        check(estimate(noise, base) == HeatVisualEstimate.Level.UNKNOWN, "isolated raster labels rejected");
        int[] mixed = tile(0xffd73838);
        for (int y = 0; y < 21; y++) for (int x = 0; x < 10; x++) mixed[y * 21 + x] = 0xff7b79d1;
        check(estimate(mixed, base) == HeatVisualEstimate.Level.UNKNOWN, "ambiguous colour boundary unknown");
        int[] labelled = tile(0xffd73838), labelledBase = base.clone();
        for (int y = 3; y < 18; y += 5) for (int x = 3; x < 18; x++) {
            labelled[y * 21 + x] = 0xffffffff; labelledBase[y * 21 + x] = 0xffffffff;
        }
        check(estimate(labelled, labelledBase) == HeatVisualEstimate.Level.HIGH, "heat survives sparse identical labels");
        check(HeatVisualEstimate.estimate(base, base, 21, 21, 0, 0, 7) == HeatVisualEstimate.Level.UNKNOWN, "offscreen unknown");
        check(HeatVisualEstimate.estimate(null, base, 21, 21, 10, 10, 7) == HeatVisualEstimate.Level.UNKNOWN, "missing snapshot unknown");
        check(HeatVisualEstimate.Level.HIGH.indicators == 5 && HeatVisualEstimate.Level.UNKNOWN.indicators == 0, "indicators are categorical");
        System.out.println("PASS " + count + " heat visual reference assertions");
    }
}
