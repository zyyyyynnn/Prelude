package com.prelude.assets.web;

import com.prelude.assets.api.AssetQueryApi;
import com.prelude.test.AccountFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetControllerTest {

    private final AssetQueryApi assetQueryApi = mock(AssetQueryApi.class);
    private final AssetController controller = new AssetController(AccountFixtures.current(9L), assetQueryApi);

    @Test
    void contentRedirectsThroughPublicAssetQueryApi() {
        when(assetQueryApi.presignedGetUrl(9L, 55L)).thenReturn("https://cdn.example/a");

        ResponseEntity<Void> response = controller.content(55L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("https://cdn.example/a"));
        verify(assetQueryApi).presignedGetUrl(9L, 55L);
    }
}
