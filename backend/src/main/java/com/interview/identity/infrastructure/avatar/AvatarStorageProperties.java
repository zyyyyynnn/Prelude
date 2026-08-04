package com.interview.identity.infrastructure.avatar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class AvatarStorageProperties {

    private Path avatarRoot = Path.of("./uploads/avatars");
    private String avatarPublicPrefix = "/media/avatars";
    private long avatarMaxBytes = 5 * 1024 * 1024;
    private int avatarMaxWidth = 2048;
    private int avatarMaxHeight = 2048;
    private long avatarMaxPixels = 4_194_304L;
}
