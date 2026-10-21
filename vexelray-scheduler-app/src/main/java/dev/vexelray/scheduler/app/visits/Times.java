package dev.vexelray.scheduler.app.visits;

/** Minutes after midnight, written the two ways a schedule writes them. */
public final class Times {

    private Times() {
    }

    /** As a clock shows it: "9:05", "14:30". */
    public static String clock(int minute) {
        return (minute / 60) + ":" + two(minute % 60);
    }

    /** As a span on a clock: "11:00–11:45". */
    public static String span(int start, int end) {
        return clock(start) + "–" + clock(end);
    }

    /** As a person says it: "2 pm", "10:30 am", "noon". */
    public static String spoken(int minute) {
        if (minute == 12 * 60) {
            return "noon";
        }
        int h = minute / 60;
        int m = minute % 60;
        int twelve = h % 12 == 0 ? 12 : h % 12;
        return twelve + (m == 0 ? "" : ":" + two(m)) + (h < 12 ? " am" : " pm");
    }

    /** An hour's tick on the schedule: "8 am", "12 pm", "1 pm". */
    public static String hour(int h) {
        int twelve = h % 12 == 0 ? 12 : h % 12;
        return twelve + (h < 12 ? " am" : " pm");
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
