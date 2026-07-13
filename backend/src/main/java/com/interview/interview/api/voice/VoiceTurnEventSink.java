package com.interview.interview.api.voice;

/**
 * Callback surface for the voice turn processing pipeline.
 *
 * The WebSocket handler supplies an implementation that maps each event back
 * onto the existing JSON wire protocol. The turn service stays decoupled from
 * Spring's WebSocketSession.
 */
public interface VoiceTurnEventSink {

    void status(String status);

    void userText(String text);

    void assistantText(String chunk);

    void audio(String base64Audio);

    void judge(int score, String hint);

    void error(String message);

    /**
     * @return the active session id currently bound to the underlying transport,
     *         or {@code null} if the user has switched sessions mid-turn.
     */
    Long currentActiveSessionId();

    void clearActiveSession();
}
