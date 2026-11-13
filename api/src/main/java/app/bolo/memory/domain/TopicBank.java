package app.bolo.memory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Conversation ideas (spec 2026-11-13, C): stories, opinions, small what-ifs, choices and light rehearsal, set in everyday
 * Bangladeshi adult life. Never routine chatter. Bolo gets 3 per talk, for when the talk needs a new direction.
 */
public final class TopicBank {

    private TopicBank() {
    }

    private static final List<String> WORK_MEETINGS = List.of(
            "A colleague takes credit for your idea in a meeting. What would you say?",
            "The best manager you've had: what did they do differently?",
            "Should meetings ever be longer than 30 minutes?",
            "Explain your job to a ten-year-old",
            "A time a plan at work went sideways, and what happened next",
            "Your team gets one extra person for a month. What should they work on?",
            "Remote work or the office: which gets more done?",
            "The most useful thing you learned in your first year at work",
            "A small change that would make your workplace better",
            "Your boss asks for a two-line update on your work right now. What do you say?",
            "A tricky customer or client you handled well",
            "If you could skip one regular meeting forever, which one and why?",
            "Disagreeing with a senior colleague politely: how would you do it?",
            "The tool or app your team couldn't work without",
            "A project you're proud of, from the first day to the end",
            "How Dhaka traffic changes the way your team works",
            "Should salaries be shared openly inside a company?",
            "Someone new joins your team tomorrow. What's your advice for their first week?",
            "What makes a meeting actually worth it?",
            "If you ran your company for a day, what would you change first?");

    private static final List<String> JOB_INTERVIEWS = List.of(
            "Tell me about yourself, in the way you'd want an interviewer to remember",
            "Why do you want this kind of job, really?",
            "A time you solved a problem nobody else wanted to touch",
            "Your biggest strength, with a story that proves it",
            "Something you'd do differently in your last job",
            "Where would you like to be in five years, and why there?",
            "The interviewer asks why you left your last job. What do you say?",
            "A time you worked with someone difficult",
            "What would your friends say you're best at?",
            "A company you'd love to work for, and what draws you to it",
            "Is it better to ask for a higher salary or a better title?",
            "A mistake that taught you something useful at work or university",
            "How you'd spend your first 30 days in a new job",
            "The question you'd ask at the end of an interview",
            "A time you had to learn something new very fast",
            "Big company or small company: where would you grow more?",
            "Describe a time you led something, even something small",
            "What does a good working day look like for you?",
            "An achievement you rarely get to talk about",
            "If you could design your perfect job, what would it include?");

    private static final List<String> STUDY_ABROAD = List.of(
            "Which country would you choose to study in, and why that one?",
            "The subject you'd study if money and marks didn't matter",
            "Your first day in a new city abroad: what would you do first?",
            "What you'd miss most about home, and how you'd handle it",
            "Explaining a Bangladeshi festival to classmates from other countries",
            "Living alone for the first time: what's the hardest part?",
            "A teacher who changed how you think",
            "Should students work part-time while studying?",
            "The visa officer asks why you chose this university. What do you say?",
            "Group projects: do they teach teamwork or just stress?",
            "Cooking for yourself abroad: which home dish would you learn first?",
            "A book, film or person that made you want to study abroad",
            "Making friends in a new country: what would you try?",
            "Is it better to come back home after studying, or stay and work?",
            "Your research or study plan in two minutes, for a professor",
            "The biggest difference you expect between classes there and here",
            "A time you had to adapt to something completely new",
            "Budgeting as a student: where would you save and where would you spend?",
            "What makes a good roommate?",
            "Scholarship interview: why should they choose you?");

    private static final List<String> DAILY_CONFIDENCE = List.of(
            "A place in Bangladesh everyone should visit at least once",
            "Cricket or football: which one gets you more excited, and why?",
            "A small win you had recently that nobody noticed",
            "The best piece of advice someone in your family gave you",
            "If you could learn any skill in a month, what would it be?",
            "A film or drama you'd recommend, without spoiling it",
            "Rickshaw, bus, CNG or walking: your favourite way around the city?",
            "A tradition from your childhood you'd like to keep alive",
            "What would you do with a completely free Friday?",
            "A time you were brave in a small way",
            "Is social media making friendships stronger or weaker?",
            "The dish you cook best, and how you make it",
            "Someone you admire who isn't famous",
            "If you could have dinner with anyone, who and what would you ask?",
            "A rule in your city you'd change tomorrow",
            "Something that always makes you laugh",
            "The most interesting person you met this year",
            "A habit you'd like to build, and what stops you",
            "Village or city: where would you rather grow old?",
            "A trip that didn't go to plan but turned out great");

    private static final List<String> GENERAL = List.of(
            "A place in Bangladesh everyone should visit at least once",
            "If you could learn any skill in a month, what would it be?",
            "A film or drama you'd recommend, without spoiling it",
            "The best piece of advice you've ever been given",
            "A small win you had recently",
            "What would you do with a completely free day?",
            "Someone you admire who isn't famous",
            "A tradition from your childhood you'd like to keep alive",
            "Is social media making friendships stronger or weaker?",
            "A rule in your city you'd change tomorrow",
            "The most useful thing you learned this year",
            "A trip that didn't go to plan but turned out great",
            "Cricket or football: which one gets you more excited?",
            "A habit you'd like to build, and what stops you",
            "If you ran the country for a day, what would you change first?",
            "The dish you cook best, and how you make it",
            "A time you were brave in a small way",
            "Village or city: where would you rather live?",
            "Something you changed your mind about",
            "A goal you're working toward right now");

    private static final Map<String, List<String>> BY_GOAL = Map.of(
            "work_meetings", WORK_MEETINGS, "job_interviews", JOB_INTERVIEWS,
            "study_abroad", STUDY_ABROAD, "daily_confidence", DAILY_CONFIDENCE);

    public static List<String> forGoal(String goal) {
        return goal == null ? GENERAL : BY_GOAL.getOrDefault(goal, GENERAL);
    }

    /** {@code n} different seeds for the goal, none of them a recent topic (compared ignoring case). */
    public static List<String> seeds(String goal, Set<String> recent, Random rnd, int n) {
        Set<String> seen = new java.util.HashSet<>();
        recent.forEach(r -> seen.add(r.toLowerCase(java.util.Locale.ROOT).strip()));
        List<String> pool = new ArrayList<>(forGoal(goal).stream()
                .filter(s -> !seen.contains(s.toLowerCase(java.util.Locale.ROOT).strip())).toList());
        java.util.Collections.shuffle(pool, rnd);
        return List.copyOf(pool.subList(0, Math.min(n, pool.size())));
    }
}
