package com.prelude.interview.application.port;

public interface VoicePort {
    /**
     * @param audioBytes PCM/Opus raw payload; {@code filename} hints the container (e.g. voice.webm)
     */
    String speechToText(Long accountId, Long sessionId, byte[] audioBytes, String filename);

    /**
     * @return MP3/WAV audio payload
     */
    byte[] textToSpeech(Long accountId, String text);
}
