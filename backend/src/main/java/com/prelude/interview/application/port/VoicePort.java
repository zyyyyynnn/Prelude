package com.prelude.interview.application.port;

public interface VoicePort {
    /**
     * Transcribe speech audio bytes to text using speech-to-text engine.
     *
     * @param accountId Owner account of the invocation
     * @param sessionId Current active session ID
     * @param audioBytes PCM/Opus raw audio payload
     * @param filename Filename representing format (e.g. voice.webm)
     * @return Transcribed text
     */
    String speechToText(Long accountId, Long sessionId, byte[] audioBytes, String filename);

    /**
     * Synthesize text content to speech audio bytes using text-to-speech engine.
     *
     * @param accountId Owner account of the invocation
     * @param text Text description
     * @return MP3/WAV audio payload
     */
    byte[] textToSpeech(Long accountId, String text);

    /**
     * Check if voice synthesis/recognition is supported under current settings.
     *
     * @return true if enabled
     */
    boolean isVoiceSupported();
}
