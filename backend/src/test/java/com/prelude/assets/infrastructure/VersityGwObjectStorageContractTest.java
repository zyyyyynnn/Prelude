package com.prelude.assets.infrastructure;

import com.prelude.assets.ObjectStoragePort;
import com.prelude.assets.infrastructure.S3StorageConfiguration.S3StorageProperties;import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 adapter contract verified against VersityGW, the local/CI S3-compatible endpoint.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_S3_SMOKE", matches = "true")
class VersityGwObjectStorageContractTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> VERSITYGW = new GenericContainer<>("versity/versitygw:v1.7.0")
        .withEnv("ROOT_ACCESS_KEY", "prelude-local-key")
        .withEnv("ROOT_SECRET_KEY", "prelude-local-secret")
        .withEnv("VGW_BACKEND", "posix")
        .withEnv("VGW_BACKEND_ARGS", "/data/s3")
        .withEnv("VGW_PORT", ":7070")
        .withEnv("VGW_IAM_DIR", "/data/iam")
        .withEnv("VGW_VERSIONING_DIR", "/data/versioning")
        .withTmpFs(java.util.Map.of(
            "/data/s3", "rw",
            "/data/iam", "rw",
            "/data/versioning", "rw"))
        .withExposedPorts(7070)
        .waitingFor(Wait.forListeningPort());

    private static S3ObjectStorageAdapter adapter;

    @BeforeAll
    static void startGateway() {
        VERSITYGW.start();
        String endpoint = "http://" + VERSITYGW.getHost() + ":" + VERSITYGW.getMappedPort(7070);
        adapter = new S3ObjectStorageAdapter(new S3StorageProperties(
            endpoint, endpoint, "us-east-1", "prelude-assets",
            "prelude-local-key", "prelude-local-secret", true, Duration.ofSeconds(120)));
    }

    @AfterAll
    static void stopGateway() {
        VERSITYGW.stop();
    }

    @Test
    void putsReadsAndDeletesObjects() {
        String objectKey = UUID.randomUUID().toString();

        adapter.put(objectKey, "text/plain", "prelude asset content".getBytes(StandardCharsets.UTF_8));

        assertThat(new String(adapter.get(objectKey), StandardCharsets.UTF_8))
            .isEqualTo("prelude asset content");

        adapter.delete(objectKey);

        HttpResponse<Void> missing = fetch(adapter.presignGet(objectKey, Duration.ofSeconds(30)));
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    @Test
    void generatedObjectKeysAreNeverReused() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        adapter.put(first, "text/plain", "first".getBytes(StandardCharsets.UTF_8));
        adapter.put(second, "text/plain", "second".getBytes(StandardCharsets.UTF_8));

        assertThat(first).isNotEqualTo(second);
        assertThat(new String(adapter.get(first), StandardCharsets.UTF_8)).isEqualTo("first");
        assertThat(new String(adapter.get(second), StandardCharsets.UTF_8)).isEqualTo("second");
    }

    @Test
    void presignedUrlsAuthorizeGetAccessUntilExpiry() throws Exception {
        String objectKey = UUID.randomUUID().toString();
        adapter.put(objectKey, "text/plain", "presign me".getBytes(StandardCharsets.UTF_8));

        String url = adapter.presignGet(objectKey, Duration.ofSeconds(120));
        assertThat(url).contains(objectKey);
        assertThat(URI.create(url).getQuery()).contains("X-Amz-Expires=120");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("presign me");

        HttpResponse<Void> unsigned = fetch("http://" + VERSITYGW.getHost() + ":"
            + VERSITYGW.getMappedPort(7070) + "/prelude-assets/" + objectKey);
        assertThat(unsigned.statusCode()).isEqualTo(403);
    }

    @Test
    void portIsAnObjectStorageBoundaryNotAnSdkMirror() {
        assertThat(ObjectStoragePort.class.getMethods())
            .extracting(java.lang.reflect.Method::getName)
            .containsExactlyInAnyOrder("put", "get", "delete", "presignGet");
    }

    private HttpResponse<Void> fetch(String url) {
        try {
            return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch " + url, exception);
        }
    }
}
