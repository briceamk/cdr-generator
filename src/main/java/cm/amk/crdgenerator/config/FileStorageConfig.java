package cm.amk.crdgenerator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "file")
@EnableConfigurationProperties({FileStorageConfig.class})
public class FileStorageConfig {
    private String storageLocation;
}
