package dev.vexelray.scheduler.app.visits;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A home-visit team's week, made up, to show the application with: the North team, eight clinicians, Tuesday to
 * Friday. Tuesday is "today" and everything on it is covered; on Thursday one wound-care visit has nobody who can
 * take it, because the one wound nurse working is at a clinic that afternoon and the other is off.
 */
public final class Sample {

    /** The day the sample calls today. */
    public static final int TODAY = 0;

    /** The minute the sample calls now: 10:40. */
    public static final int NOW = 10 * 60 + 40;

    private static final String[] STREETS = {"Elm Street", "Harbor Road", "Linden Avenue", "Mill Lane", "Orchard Way",
        "Cedar Court", "Bay View Drive", "Station Road"};

    private final List<Visit> visits = new ArrayList<>();
    private final List<Away> away = new ArrayList<>();

    private Sample() {
    }

    public static Team north() {
        Sample s = new Sample();
        s.day(0, "Dana Reyes", "8:30 9:15 J. Patel WOUND", "9:40 10:30 T. Brooks WOUND",
                "11:00 11:45 M. Okafor WOUND ES", "13:00 13:45 H. Ngata NURSE", "14:30 15:15 L. Hale WOUND");
        s.day(0, "Sam Whitaker", "8:00 8:45 F. Moreau NURSE", "9:15 10:00 S. Ibsen NURSE", "10:30 11:15 R. Chen NURSE",
                "12:30 13:15 D. Ward NURSE", "14:00 14:45 P. Quinn NURSE");
        s.day(0, "Grace Kim", "9:00 10:00 E. Lund PEDS", "10:45 11:45 A. Diaz PEDS", "13:30 14:30 K. Frost PEDS",
                "15:00 16:00 N. Abara PEDS");
        s.day(0, "Marcus Lee", "8:30 9:30 B. Varga PT", "10:00 11:00 O. Holm PT", "12:00 13:00 C. Soto PT",
                "14:00 15:00 W. Dunn PT", "15:30 16:30 Y. Yeo PT");
        s.day(0, "Ana Torres", "9:00 9:45 G. Ruiz NURSE ES", "10:15 11:00 I. Pena NURSE ES",
                "11:30 12:15 V. Marin NURSE", "13:30 14:15 E. Vega NURSE ES");
        s.day(0, "Joe Becker", "8:00 10:00 S. Kowal HHA", "11:00 13:00 R. Ames HHA", "14:00 16:00 U. Brandt HHA");
        s.day(0, "Lena Fischer", "9:30 10:30 M. Rossi OT", "11:00 12:00 T. Unger OT", "13:30 14:30 C. Eze OT");

        s.day(1, "Dana Reyes", "8:30 9:15 L. Hale WOUND", "10:00 10:45 J. Patel WOUND", "13:00 13:45 T. Brooks WOUND");
        s.day(1, "Sam Whitaker", "8:00 8:45 D. Ward NURSE", "9:30 10:15 H. Ngata NURSE", "11:00 11:45 F. Moreau NURSE",
                "14:00 14:45 S. Ibsen NURSE");
        s.day(1, "Grace Kim", "9:00 10:00 K. Frost PEDS", "11:00 12:00 N. Abara PEDS", "14:00 15:00 E. Lund PEDS");
        s.day(1, "Marcus Lee", "8:30 9:30 W. Dunn PT", "10:00 11:00 B. Varga PT", "13:00 14:00 O. Holm PT");
        s.day(1, "Ana Torres", "9:00 9:45 V. Marin NURSE", "10:30 11:15 G. Ruiz NURSE ES",
                "13:00 13:45 I. Pena NURSE ES");
        s.day(1, "Joe Becker", "8:00 10:00 R. Ames HHA", "11:00 13:00 S. Kowal HHA");
        s.day(1, "Lena Fischer", "10:00 11:00 T. Unger OT", "13:30 14:30 M. Rossi OT");

        s.day(2, "Dana Reyes", "8:30 9:15 J. Patel WOUND", "9:40 10:25 H. Ngata NURSE", "10:45 11:30 L. Hale WOUND");
        s.away.add(new Away("Dana Reyes", 2, 13 * 60, 17 * 60, "at the Pediatrics clinic"));
        s.day(2, "Sam Whitaker", "8:00 8:45 F. Moreau NURSE", "9:15 10:00 R. Chen NURSE", "11:00 11:45 D. Ward NURSE",
                "13:30 14:15 P. Quinn NURSE");
        s.day(2, "Grace Kim", "9:00 10:00 E. Lund PEDS", "10:30 11:30 A. Diaz PEDS", "13:30 14:30 K. Frost PEDS");
        s.day(2, "Marcus Lee", "8:30 9:30 B. Varga PT", "10:00 11:00 O. Holm PT", "12:30 13:30 C. Soto PT",
                "14:30 15:30 Y. Yeo PT");
        s.day(2, "Ana Torres", "9:00 9:45 G. Ruiz NURSE ES", "10:15 11:00 I. Pena NURSE ES",
                "13:00 13:45 V. Marin NURSE");
        s.day(2, "Joe Becker", "8:00 10:00 S. Kowal HHA", "11:00 13:00 R. Ames HHA", "14:00 16:00 U. Brandt HHA");
        s.day(2, "Lena Fischer", "9:30 10:30 M. Rossi OT", "13:30 14:30 C. Eze OT");
        // Nobody has it, and it may happen any time from Thursday at 2 to Friday noon.
        s.day(2, "", "14:00 14:45 Mrs. Alvarez WOUND");
        Visit alvarez = s.visits.removeLast();
        s.visits.add(new Visit(alvarez.id(), alvarez.patient(), alvarez.address(), 2, alvarez.start(), alvarez.end(),
                alvarez.needs(), Optional.empty(), Team.at(2, 14 * 60), Team.at(3, 12 * 60)));

        s.day(3, "Dana Reyes", "8:00 8:45 J. Patel WOUND", "9:05 9:40 L. Hale WOUND", "11:15 12:00 M. Okafor WOUND ES",
                "13:30 14:15 T. Brooks WOUND");
        s.day(3, "Priya Nair", "8:00 8:45 F. Moreau WOUND", "9:10 9:55 S. Ibsen NURSE", "10:30 11:15 P. Quinn WOUND");
        s.day(3, "Sam Whitaker", "8:30 9:15 D. Ward NURSE", "10:00 10:45 R. Chen NURSE", "13:00 13:45 H. Ngata NURSE");
        s.day(3, "Grace Kim", "9:00 10:00 A. Diaz PEDS", "11:00 12:00 E. Lund PEDS");
        s.day(3, "Marcus Lee", "8:30 9:30 Y. Yeo PT", "10:00 11:00 C. Soto PT", "14:00 15:00 W. Dunn PT");
        s.day(3, "Ana Torres", "9:00 9:45 I. Pena NURSE ES", "13:00 13:45 E. Vega NURSE ES");
        s.day(3, "Joe Becker", "8:00 10:00 U. Brandt HHA", "11:00 13:00 S. Kowal HHA");
        s.day(3, "Lena Fischer", "9:30 10:30 C. Eze OT", "13:30 14:30 T. Unger OT");

        List<Clinician> clinicians = List.of(
                clinician("Dana Reyes", "RN WOUND ES", "NURSE WOUND ES"),
                clinician("Sam Whitaker", "LPN", "NURSE"),
                new Clinician("Priya Nair", List.of("RN", "WOUND"), Set.of("NURSE", "WOUND"), Set.of(0, 1, 2)),
                clinician("Grace Kim", "RN PEDS", "NURSE PEDS"),
                clinician("Marcus Lee", "PT", "PT"),
                clinician("Ana Torres", "LPN ES", "NURSE ES"),
                clinician("Joe Becker", "HHA", "HHA"),
                clinician("Lena Fischer", "OT", "OT"));
        return new Team("North team", List.of("Tue 21 Oct", "Wed 22 Oct", "Thu 23 Oct", "Fri 24 Oct"), clinicians,
                s.visits, s.away, 20);
    }

    private static Clinician clinician(String name, String credentials, String tags) {
        return new Clinician(name, List.of(credentials.split(" ")), Set.of(tags.split(" ")), Set.of());
    }

    /** {@code name}'s visits on {@code day}, each "start end patient NEED...". An empty name: nobody has them. */
    private void day(int day, String name, String... lines) {
        for (String line : lines) {
            String[] t = line.split(" ");
            List<Need> needs = new ArrayList<>();
            StringBuilder patient = new StringBuilder();
            for (int i = 2; i < t.length; i++) {
                if (t[i].matches("[A-Z]{2,}")) {
                    needs.add(Need.of(t[i]));
                } else {
                    patient.append(patient.isEmpty() ? "" : " ").append(t[i]);
                }
            }
            int start = minute(t[0]);
            int end = minute(t[1]);
            int n = visits.size();
            String address = (4 + (n * 7) % 90) + " " + STREETS[n % STREETS.length];
            visits.add(new Visit("v" + n, patient.toString(), address, day, start, end, needs,
                    name.isEmpty() ? Optional.empty() : Optional.of(name), Team.at(day, start), Team.at(day, end)));
        }
    }

    private static int minute(String clock) {
        String[] hm = clock.split(":");
        return Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
    }
}
