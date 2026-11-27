package com.prelude.voice.application;

public interface VoicePort {

    String speechToText(Long accountId, byte[] audioBytes, String filename);

    byte[] textToSpeech(Long accountId, String text);
}
