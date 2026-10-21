package dev.vexelray.scheduler.app.visits;

import dev.vexelray.scheduler.solver.Assigner;
import dev.vexelray.scheduler.solver.Assignment;
import dev.vexelray.scheduler.solver.Plan;
import dev.vexelray.scheduler.solver.Rule;
import dev.vexelray.scheduler.solver.Solution;
import dev.vexelray.scheduler.solver.Solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What is going on with one day of a schedule, in sentences: which visits are covered, which need someone, why each
 * clinician has the visits they have, and what would fix the ones that need someone.
 *
 * <h2>Where the sentences come from</h2>
 *
 * <p>Every sentence here stands on an answer from the solver, and the solver's answers have been through its
 * checker. Whether a clinician can fit a visit into their day is a temporal network ({@link Solver}): their visits
 * and time away at their booked times, with the team's drive between each and the next; a cycle in it is the
 * evidence they cannot, and the constraint in the cycle that touches the visit names what is in the way. Whether a
 * clinician may take a visit is a {@link Plan}: the visit's needs are {@code require} rules, a day off and a full
 * diary are {@code forbid} rules, and {@link Plan#excluding} gives back exactly the rules that rule someone out,
 * which become "isn't certified in wound care" and "is off Thursday". A visit nobody has yet goes to the
 * {@link Assigner}, which finds someone or returns the shortage that proves there is nobody.
 *
 * <p>The person reading never sees a rule, a tag or a cycle. That is the point.
 */
public final class Review {

    /** One visit, checked. */
    public record Check(Visit visit, Optional<Clinician> clinician, boolean covered, String problem,
            String explanation, List<Reason> why, List<Other> others, List<Fix> fixes) {
    }

    /** One thing the visit needs, and how its clinician meets it. */
    public record Reason(String need, String how) {
    }

    /** Someone else, and whether they could take the visit. */
    public record Other(String who, boolean could, String why) {
    }

    /**
     * A way to fix a visit that needs someone. Nothing has changed until {@link #after} is committed.
     *
     * @param change what moves, in a line: "Thu 2 pm → Fri 10 am, Dana"
     */
    public record Fix(String title, String detail, String change, Team after) {
    }

    /** Something happening at the moment the review was asked for. */
    public record Now(String what, String detail) {
    }

    /** The whole day. */
    public record Day(Team team, int day, List<Check> checks, String headline, String detail, List<String> later) {

        public List<Check> problems() {
            return checks.stream().filter(c -> !c.covered()).toList();
        }

        public Optional<Check> check(String visitId) {
            return checks.stream().filter(c -> c.visit().id().equals(visitId)).findFirst();
        }
    }

    /** The first visit of a day may start this early, when a fix looks for a time. */
    static final int OPENS = 8 * 60;

    /** And end this late. */
    static final int CLOSES = 17 * 60;

    /** A fix looks for times on this grid, in minutes. */
    static final int STEP = 15;

    private static final String OFF = "off";
    private static final String VISIT = "visit";

    private final Team team;
    private final int day;
    private final int today;

    private Review(Team team, int day, int today) {
        this.team = team;
        this.day = day;
        this.today = today;
    }

    /** {@code day} of {@code team}, with {@code today} the day that is called "today", and the days after it looked over. */
    public static Day of(Team team, int day, int today) {
        return new Review(team, day, today).day(true);
    }

    /** What is happening at {@code minute} on {@code day}: who is with whom, who is on the way. At most three. */
    public static List<Now> now(Team team, int day, int minute) {
        List<Now> out = new ArrayList<>();
        for (Clinician c : team.working(day)) {
            for (Visit v : team.visits(day, c.name())) {
                if (v.start() <= minute && minute < v.end()) {
                    out.add(new Now(c.first() + " is with " + v.patient(),
                            v.care() + " · until " + Times.clock(v.end())));
                    break;
                }
                if (minute < v.start() && v.start() - minute <= team.travel() + STEP) {
                    out.add(new Now(c.first() + " is on the way to " + v.patient(),
                            v.care() + " at " + Times.clock(v.start())));
                    break;
                }
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ the day

    private Day day(boolean outlook) {
        List<Visit> visits = team.visits(day);
        Situation s = situation(visits);
        List<Check> checks = new ArrayList<>(visits.size());
        for (Visit v : visits) {
            checks.add(check(v, s));
        }
        int problems = (int) checks.stream().filter(c -> !c.covered()).count();
        String headline;
        String detail;
        String when = when(day);
        int working = team.working(day).size();
        if (problems == 0) {
            headline = "Everything " + when + " is covered.";
            detail = count(visits.size(), "visit") + ", " + count(working, "clinician") + " working. Every visit has "
                    + "someone qualified for it, and everyone can get from one visit to the next in time.";
        } else {
            headline = problems == 1 ? "1 visit needs you." : problems + " visits need you.";
            detail = count(visits.size(), "visit") + ", " + count(working, "clinician") + " working. "
                    + (visits.size() - problems) + " of them are covered.";
        }
        return new Day(team, day, List.copyOf(checks), headline, detail, outlook ? later() : List.of());
    }

    /** The days after this one, in a line each, and who comes back. */
    private List<String> later() {
        List<String> out = new ArrayList<>();
        for (int d = day + 1; d < team.days().size(); d++) {
            Day next = new Review(team, d, today).day(false);
            List<Check> problems = next.problems();
            if (!problems.isEmpty()) {
                Check first = problems.get(0);
                out.add(team.weekday(d) + " has " + (problems.size() == 1 ? "a visit" : problems.size() + " visits")
                        + " that needs you: " + possessive(first.visit().patient()) + " "
                        + first.visit().care().toLowerCase() + " at " + Times.spoken(first.visit().start()) + ".");
            }
        }
        for (Clinician c : team.clinicians()) {
            if (!c.works(day)) {
                for (int d = day + 1; d < team.days().size(); d++) {
                    if (c.works(d)) {
                        out.add(c.first() + " is back on " + team.weekday(d) + ".");
                        break;
                    }
                }
            }
        }
        if (out.isEmpty() && day + 1 < team.days().size()) {
            out.add("The rest of the week is covered.");
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ one visit

    private Check check(Visit v, Situation s) {
        int d = s.demand.get(v.id());
        Optional<Clinician> who = v.clinician().flatMap(team::clinician);
        if (who.isPresent()) {
            Clinician c = who.get();
            int r = team.clinicians().indexOf(c);
            if (s.plan.eligible(d, r)) {
                return new Check(v, who, true, "", "", why(v, c), others(v, c, s), List.of());
            }
            String problem = possessive(v.patient()) + " " + v.care().toLowerCase() + " visit at "
                    + Times.spoken(v.start()) + " needs someone else.";
            String explanation = c.first() + " " + and(reasons(s, d, r)) + ".";
            return new Check(v, who, false, problem, explanation, List.of(), others(v, c, s), fixes(v, s));
        }
        // Nobody has it: the assigner finds someone, or the shortage that proves there is nobody.
        Situation alone = situation(List.of(v));
        Assignment assignment = Assigner.assign(alone.plan);
        String problem;
        String explanation;
        if (assignment.shortage().isPresent()) {
            problem = possessive(v.patient()) + " " + v.care().toLowerCase() + " visit at "
                    + Times.spoken(v.start()) + " has nobody who can take it.";
            explanation = shortage(v, alone);
        } else {
            Clinician found = team.clinicians().get(assignment.resources().orElseThrow()[0]);
            problem = possessive(v.patient()) + " " + v.care().toLowerCase() + " visit at "
                    + Times.spoken(v.start()) + " has nobody yet.";
            explanation = found.first() + " is free then and has everything it needs.";
        }
        return new Check(v, Optional.empty(), false, problem, explanation, List.of(), others(v, null, s),
                fixes(v, s));
    }

    /** Why {@code c} may have {@code v}: each need, and getting there in time. */
    private List<Reason> why(Visit v, Clinician c) {
        List<Reason> out = new ArrayList<>();
        for (Need need : v.needs()) {
            out.add(new Reason(need.asked(), c.first() + " " + need.has() + "."));
        }
        Visit before = null;
        for (Visit w : team.visits(day, c.name())) {
            if (w.start() < v.start()) {
                before = w;
            }
        }
        out.add(new Reason("There by " + Times.clock(v.start()), before == null
                ? "It is " + possessive(c.first()) + " first visit of the day."
                : possessive(c.first()) + " visit before ends at " + Times.clock(before.end())
                        + ", and the drive is about " + team.travel() + " minutes."));
        return out;
    }

    /** Everyone else, and why they could or could not take {@code v}; the ones it is no use naming, counted. */
    private List<Other> others(Visit v, Clinician holder, Situation s) {
        int d = s.demand.get(v.id());
        List<Other> out = new ArrayList<>();
        int unqualified = 0;
        for (int r = 0; r < team.clinicians().size(); r++) {
            Clinician c = team.clinicians().get(r);
            if (c.equals(holder)) {
                continue;
            }
            if (s.plan.eligible(d, r)) {
                out.add(new Other(c.name(), true, "Free then, with everything it needs."));
                continue;
            }
            List<String> has = v.needs().stream().filter(c::has).map(Need::has).toList();
            if (has.isEmpty()) {
                unqualified++;
                continue;
            }
            out.add(new Other(c.name(), false, capital(and(has)) + ", but " + and(reasons(s, d, r)) + "."));
        }
        if (unqualified > 0) {
            out.add(new Other(unqualified == 1 ? "1 other" : unqualified + " others", false,
                    "Not qualified for it: " + v.needs().get(0).asked().toLowerCase() + " is a must."));
        }
        return out;
    }

    /** Why nobody can take {@code v}: what it needs, and where everyone who has it is. */
    private String shortage(Visit v, Situation alone) {
        StringBuilder out = new StringBuilder("It needs ");
        out.append(and(v.needs().stream().map(n -> n.asked().toLowerCase()).toList())).append('.');
        List<String> where = new ArrayList<>();
        for (int r = 0; r < team.clinicians().size(); r++) {
            Clinician c = team.clinicians().get(r);
            if (v.needs().stream().allMatch(c::has)) {
                where.add(c.first() + " " + and(reasons(alone, 0, r)));
            }
        }
        if (where.isEmpty()) {
            out.append(" Nobody on ").append(team.name()).append(" has all of that.");
        } else {
            out.append(' ').append(and(where)).append('.');
        }
        return out.toString();
    }

    /** The rules that rule {@code r} out of demand {@code d}, as what is said after the clinician's name. */
    private List<String> reasons(Situation s, int d, int r) {
        Set<String> out = new LinkedHashSet<>();
        for (Rule rule : s.plan.excluding(d, r)) {
            out.add(s.say(rule, r));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------ fixes

    private List<Fix> fixes(Visit v, Situation s) {
        List<Fix> out = new ArrayList<>();
        int d = s.demand.get(v.id());
        for (int r = 0; r < team.clinicians().size(); r++) {
            Clinician c = team.clinicians().get(r);
            if (s.plan.eligible(d, r) && v.clinician().filter(c.name()::equals).isEmpty()) {
                out.add(new Fix("Give it to " + c.first(), c.first() + " is free then and "
                        + and(v.needs().stream().map(Need::has).toList()) + ". Nothing else moves.",
                        v.label() + " · " + Times.spoken(v.start()) + " → " + c.first(),
                        team.assign(v.id(), c.name())));
                break;
            }
        }
        moved(v).ifPresent(out::add);
        for (int r = 0; r < team.clinicians().size(); r++) {
            Clinician c = team.clinicians().get(r);
            List<Rule> excluding = s.plan.excluding(d, r);
            if (!c.works(day) && excluding.size() == 1 && excluding.get(0).resourceTag().equals(OFF)) {
                out.add(new Fix("Ask " + c.first() + " to take it", c.first() + " "
                        + and(v.needs().stream().map(Need::has).toList()) + " and is off " + team.weekday(day)
                        + ". It would be " + possessive(c.first()) + " only visit that day.",
                        v.label() + " · " + Times.spoken(v.start()) + " → " + c.first() + ", called in",
                        team.callIn(c.name(), day).assign(v.id(), c.name())));
            }
        }
        return out;
    }

    /** The first time within the visit's window when someone working can take it, nothing else moving. */
    private Optional<Fix> moved(Visit v) {
        int lastDay = Math.min(v.due() / Team.DAY, team.days().size() - 1);
        for (int d = v.day(); d <= lastDay; d++) {
            for (int t = OPENS; t + v.length() <= CLOSES; t += STEP) {
                int at = Team.at(d, t);
                if (at < v.earliest() || at + v.length() > v.due() || (d == v.day() && t == v.start())) {
                    continue;
                }
                Team candidate = team.move(v.id(), d, t, "");
                Visit there = candidate.visit(v.id()).orElseThrow();
                Review review = new Review(candidate, d, today);
                Situation alone = review.situation(List.of(there));
                Assignment found = Assigner.assign(alone.plan);
                if (found.resources().isPresent()) {
                    Clinician c = team.clinicians().get(found.resources().get()[0]);
                    String when = (d == v.day() ? "" : team.weekday(d) + " at ") + Times.spoken(t);
                    String due = team.weekday(v.due() / Team.DAY) + " " + Times.spoken(v.due() % Team.DAY);
                    return Optional.of(new Fix("Move it to " + when, c.first() + " is free then, and the visit can "
                            + "happen any time up to " + due + ". Nothing else moves.",
                            v.label() + " · " + team.days().get(v.day()).substring(0, 3) + " "
                                    + Times.spoken(v.start()) + " → " + team.days().get(d).substring(0, 3) + " "
                                    + Times.spoken(t) + ", " + c.first(),
                            team.move(v.id(), d, t, c.name())));
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------------------------------ the solver

    /** The plan for some of the day's visits, and what each of its rules means in words. */
    private final class Situation {

        final Plan plan;

        /** Each visit's demand in {@link #plan}, by visit id. */
        final Map<String, Integer> demand = new HashMap<>();

        /** What keeps clinician {@code r} from visit {@code id}, by {@code r + ":" + id}. */
        final Map<String, String> busy = new HashMap<>();

        Situation(List<Visit> visits) {
            Plan.Builder b = new Plan.Builder(Team.DAY);
            for (Need need : Need.ALL) {
                b.require(need.tag(), need.tag());
            }
            b.forbid(VISIT, OFF);
            for (Visit v : visits) {
                List<String> tags = new ArrayList<>();
                for (Need need : v.needs()) {
                    tags.add(need.tag());
                }
                tags.add(VISIT);
                tags.add(tag(v));
                // One demand a job, so a visit's demand has its job's index.
                demand.put(v.id(), b.job(v.length(), tags.toArray(String[]::new)));
                b.forbid(tag(v), "busy:" + v.id());
            }
            for (int r = 0; r < team.clinicians().size(); r++) {
                Clinician c = team.clinicians().get(r);
                List<String> tags = new ArrayList<>(c.tags());
                if (!c.works(day)) {
                    tags.add(OFF);
                } else {
                    for (Visit v : visits) {
                        Optional<String> blocker = blocker(c, v);
                        if (blocker.isPresent()) {
                            tags.add("busy:" + v.id());
                            busy.put(r + ":" + v.id(), blocker.get());
                        }
                    }
                }
                b.resource(tags.toArray(String[]::new));
            }
            plan = b.build();
        }

        /** What a rule ruling out clinician {@code r} says about them. */
        String say(Rule rule, int r) {
            if (rule.kind() == Rule.Kind.REQUIRE) {
                return Need.of(rule.demandTag()).lacks();
            }
            if (rule.resourceTag().equals(OFF)) {
                return "is off " + when(day);
            }
            return busy.get(r + ":" + rule.resourceTag().substring("busy:".length()));
        }

        private static String tag(Visit v) {
            return "v:" + v.id();
        }
    }

    private Situation situation(List<Visit> visits) {
        return new Situation(visits);
    }

    /** Something in a day: a visit or time away. */
    private record Item(int start, int end, String what) {
    }

    /**
     * Whether {@code c} can fit {@code v} into their day, as the solver decides it: their day with {@code v} in it
     * is a temporal network, and the conflict it returns when it has no schedule names what is in the way.
     */
    private Optional<String> blocker(Clinician c, Visit v) {
        List<Item> items = new ArrayList<>();
        for (Visit w : team.visits(day, c.name())) {
            if (!w.id().equals(v.id())) {
                items.add(new Item(w.start(), w.end(), "with " + w.patient() + " at " + Times.clock(w.start())));
            }
        }
        for (Away a : team.away(day, c.name())) {
            items.add(new Item(a.start(), a.end(), a.what() + " then"));
        }
        Item candidate = new Item(v.start(), v.end(), "");
        items.add(candidate);
        items.sort(Comparator.comparingInt(Item::start).thenComparingInt(Item::end));

        Plan.Builder b = new Plan.Builder(Team.DAY);
        int[] jobs = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            jobs[i] = b.job(item.end() - item.start());
            b.release(jobs[i], item.start());
            b.deadline(jobs[i], item.end());
        }
        // Constraint index → the pair of items it keeps apart.
        Map<Integer, int[]> gaps = new HashMap<>();
        for (int i = 0; i + 1 < items.size(); i++) {
            gaps.put(b.after(jobs[i], jobs[i + 1], team.travel()), new int[] {i, i + 1});
        }
        Solution solution = Solver.solve(b.build().network());
        if (solution.schedule().isPresent()) {
            return Optional.empty();
        }
        int self = items.indexOf(candidate);
        for (int k : solution.conflict().orElseThrow().cycle()) {
            int[] pair = gaps.get(k);
            if (pair != null && (pair[0] == self || pair[1] == self)) {
                return Optional.of("is " + items.get(pair[0] == self ? pair[1] : pair[0]).what());
            }
        }
        return Optional.of("can't fit it in");
    }

    // ------------------------------------------------------------------------------------------------ words

    private String when(int d) {
        return d == today ? "today" : team.weekday(d);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    static String possessive(String name) {
        return name + (name.endsWith("s") ? "'" : "'s");
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "a", "a and b", "a, b and c". */
    static String and(List<String> parts) {
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "" : parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }
}
