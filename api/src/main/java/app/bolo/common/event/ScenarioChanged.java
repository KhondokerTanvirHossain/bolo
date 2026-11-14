package app.bolo.common.event;

import app.bolo.common.id.LearnerId;

/** SCN-1: the learner switched between Free talk and a Standup rehearsal (analytics: scenario_changed). */
public record ScenarioChanged(LearnerId learner, String from, String to, String difficulty) {
}
