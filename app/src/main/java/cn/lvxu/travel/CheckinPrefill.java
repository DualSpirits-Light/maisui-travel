package cn.lvxu.travel;

/** Immutable context used when a place starts a new check-in. */
final class CheckinPrefill {
    static final class Draft {
        final Trip trip;
        final String place;
        Draft(Trip trip, String place) { this.trip = trip; this.place = place; }
    }

    private CheckinPrefill() { }

    static Draft forStop(Trip trip, Trip.Stop stop) {
        if (trip == null || stop == null) throw new IllegalArgumentException("未找到所属旅行或地点");
        return new Draft(trip, stop.name == null ? "" : stop.name.trim());
    }
}
