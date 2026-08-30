package com.prelude.llm;

import com.prelude.BusinessException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LlmMultimodalMessages {

    private LlmMultimodalMessages() {
    }

    static List<Map<String, Object>> openAiChat(
        List<Map<String, String>> messages,
        List<LlmAttachment> attachments
    ) {
        List<Map<String, Object>> result = copyMessages(messages);
        int index = lastUserIndex(result);
        String text = String.valueOf(result.get(index).getOrDefault("content", ""));
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        for (LlmAttachment attachment : attachments) {
            content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl(attachment))
            ));
        }
        result.get(index).put("content", content);
        return result;
    }

    static List<Map<String, Object>> openAiResponses(
        List<Map<String, String>> messages,
        List<LlmAttachment> attachments
    ) {
        List<Map<String, Object>> result = copyMessages(messages);
        int index = lastUserIndex(result);
        String text = String.valueOf(result.get(index).getOrDefault("content", ""));
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", text));
        for (LlmAttachment attachment : attachments) {
            content.add(Map.of("type", "input_image", "image_url", dataUrl(attachment)));
        }
        result.get(index).put("content", content);
        return result;
    }

    static List<Map<String, Object>> anthropic(
        List<Map<String, String>> messages,
        List<LlmAttachment> attachments
    ) {
        List<Map<String, Object>> result = copyMessages(messages.stream()
            .filter(message -> !"system".equals(message.get("role")))
            .toList());
        int index = lastUserIndex(result);
        String text = String.valueOf(result.get(index).getOrDefault("content", ""));
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        for (LlmAttachment attachment : attachments) {
            content.add(Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "base64",
                    "media_type", attachment.mediaType(),
                    "data", Base64.getEncoder().encodeToString(attachment.content())
                )
            ));
        }
        result.get(index).put("content", content);
        return result;
    }

    private static List<Map<String, Object>> copyMessages(List<Map<String, String>> messages) {
        return messages.stream()
            .map(message -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                copy.putAll(message);
                return copy;
            })
            .toList();
    }

    private static int lastUserIndex(List<Map<String, Object>> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if ("user".equals(messages.get(index).get("role"))) return index;
        }
        throw BusinessException.badRequest("图片附件缺少可关联的用户消息");
    }

    private static String dataUrl(LlmAttachment attachment) {
        return "data:" + attachment.mediaType() + ";base64,"
            + Base64.getEncoder().encodeToString(attachment.content());
    }
}
