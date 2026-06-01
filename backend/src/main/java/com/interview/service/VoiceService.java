package com.interview.service;

public interface VoiceService {
    /**
     * Transcribe speech audio bytes to text using speech-to-text engine.
     *
     * @param sessionId Current active session ID
     * @param audioBytes PCM/Opus raw audio payload
     * @param filename Filename representing format (e.g. voice.webm)
     * @return Transcribed text
     */
    String speechToText(Long sessionId, byte[] audioBytes, String filename);

    /**
     * Synthesize text content to speech audio bytes using text-to-speech engine.
     *
     * @param text Text description
     * @return MP3/WAV audio payload
     */
    byte[] textToSpeech(String text);

    /**
     * Check if voice synthesis/recognition is supported under current settings.
     *
     * @return true if enabled
     */
    boolean isVoiceSupported();
}
