package app.bolo.admin.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The admin's read model (P2a): reports across the tables, read-only. */
public interface AdminReportsPort {

    record User(String id, String email, String name, String kind, boolean unlimited, String plan, Instant planEndsAt,
                int talksThisMonth, Instant lastTalkAt) {
    }

    record Day(LocalDate date, int talks, int minutes, int newAccounts, BigDecimal costUsd) {
    }

    List<User> users(String query, Instant monthStart);

    List<Day> days(LocalDate from, LocalDate to);

    int liveTalks(Instant since);

    /** DBF-8: "How was today?" — average and share of 4–5 since {@code since} (skips left out), and the newest notes. */
    record Ratings(Double average30, Double share45, int count30, List<Note> latest) {
    }

    record Note(int score, String note, Instant at) {
    }

    Ratings ratings(Instant since, int notes);
}
