package com.interview.platform.llm;

import com.interview.shared.api.LlmServerException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class CustomLlmHttpClient {

    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final int MAX_STREAM_LINE_CHARS = 256 * 1024;

    private final CustomLlmEgressPolicy policy;
    private final OkHttpClient client;

    public CustomLlmHttpClient(CustomLlmEgressPolicy policy) {
        this.policy = policy;
        this.client = new OkHttpClient.Builder()
            .dns(policy::guardedLookup)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    }

    public Response execute(Request request, Duration readTimeout) throws IOException {
        policy.validateUrl(request.url());
        return client.newBuilder()
            .readTimeout(readTimeout)
            .callTimeout(readTimeout.plusSeconds(20))
            .build()
            .newCall(request)
            .execute();
    }

    public String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        long declaredLength = body.contentLength();
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw responseTooLarge();
        }
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw responseTooLarge();
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    public BufferedReader openStreamReader(ResponseBody body) {
        if (body == null) {
            throw new LlmServerException("模型服务流式响应为空");
        }
        long declaredLength = body.contentLength();
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw responseTooLarge();
        }
        return new BufferedReader(new InputStreamReader(
            new BoundedInputStream(body.byteStream(), MAX_RESPONSE_BYTES),
            StandardCharsets.UTF_8
        ));
    }

    public String readStreamLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') {
                break;
            }
            if (character != '\r') {
                if (line.length() >= MAX_STREAM_LINE_CHARS) {
                    throw responseTooLarge();
                }
                line.append((char) character);
            }
        }
        return character == -1 && line.isEmpty() ? null : line.toString();
    }

    private LlmServerException responseTooLarge() {
        return new LlmServerException("模型服务响应超过安全上限");
    }

    private final class BoundedInputStream extends FilterInputStream {

        private final int maximumBytes;
        private int consumedBytes;

        private BoundedInputStream(InputStream input, int maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                recordRead(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                recordRead(read);
            }
            return read;
        }

        private void recordRead(int count) {
            consumedBytes += count;
            if (consumedBytes > maximumBytes) {
                throw responseTooLarge();
            }
        }
    }
}
