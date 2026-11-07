package app.bolo.talk.application.port.in;

import app.bolo.common.id.TalkSessionId;

/** A live talk as the relay and the web client know it. goal/fearedMoment are null without a profile. */
public record TalkView(TalkSessionId id, int minutes, String goal, String fearedMoment) {
}
