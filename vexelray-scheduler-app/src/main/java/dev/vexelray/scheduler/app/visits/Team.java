package dev.vexelray.scheduler.app.visits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One schedule: a team's clinicians, their visits and their time away, over a run of days. Immutable; every change
 * is a new team, which is what lets the application undo one.
 *
 * @param days   how each day is written in a heading: "Tue 21 Oct"
 * @param travel the drive between two visits, in minutes, as the team plans it
 */
public record Team(String name, List<String> days, List<Clinician> clinicians, List<Visit> visits, List<Away> away,
        int travel) {

    /** Minutes in a day: the solver's horizon, and how {@link #at} counts across days. */
    public static final int DAY = 24 * 60;

    public Team {
        days = List.copyOf(days);
        clinicians = List.copyOf(clinicians);
        visits = List.copyOf(visits);
        away = List.copyOf(away);
    }

    /** Minute {@code minute} of {@code day}, counted from the start of the first day. */
    public static int at(int day, int minute) {
        return day * DAY + minute;
    }

    /** "Thursday", from "Thu 23 Oct". */
    public String weekday(int day) {
        return switch (days.get(day).substring(0, 3)) {
            case "Mon" -> "Monday";
            case "Tue" -> "Tuesday";
            case "Wed" -> "Wednesday";
            case "Thu" -> "Thursday";
            case "Fri" -> "Friday";
            case "Sat" -> "Saturday";
            case "Sun" -> "Sunday";
            default -> days.get(day);
        };
    }

    public Optional<Clinician> clinician(String name) {
        return clinicians.stream().filter(c -> c.name().equals(name)).findFirst();
    }

    public Optional<Visit> visit(String id) {
        return visits.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    /** The day's visits, by start. */
    public List<Visit> visits(int day) {
        return visits.stream().filter(v -> v.day() == day).sorted(Comparator.comparingInt(Visit::start)).toList();
    }

    /** {@code name}'s visits that day, by start. */
    public List<Visit> visits(int day, String name) {
        return visits(day).stream().filter(v -> v.clinician().filter(name::equals).isPresent()).toList();
    }

    /** {@code name}'s time away that day, by start. */
    public List<Away> away(int day, String name) {
        return away.stream().filter(a -> a.day() == day && a.clinician().equals(name))
                .sorted(Comparator.comparingInt(Away::start)).toList();
    }

    public List<Clinician> working(int day) {
        return clinicians.stream().filter(c -> c.works(day)).toList();
    }

    /** The visit {@code id} given to {@code name}. */
    public Team assign(String id, String name) {
        return withVisit(id, v -> v.to(name));
    }

    /** The visit {@code id} moved to {@code start} on {@code day} and given to {@code name}. */
    public Team move(String id, int day, int start, String name) {
        return withVisit(id, v -> v.at(day, start).to(name));
    }

    /** {@code name} working on {@code day}, which was a day off. */
    public Team callIn(String name, int day) {
        List<Clinician> next = new ArrayList<>(clinicians.size());
        for (Clinician c : clinicians) {
            next.add(c.name().equals(name) ? c.working(day) : c);
        }
        return new Team(this.name, days, next, visits, away, travel);
    }

    private Team withVisit(String id, java.util.function.UnaryOperator<Visit> change) {
        List<Visit> next = new ArrayList<>(visits.size());
        for (Visit v : visits) {
            next.add(v.id().equals(id) ? change.apply(v) : v);
        }
        return new Team(name, days, clinicians, next, away, travel);
    }
}
