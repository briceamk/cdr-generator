package cm.amk.crdgenerator;

import cm.amk.crdgenerator.config.FileStorageConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({FileStorageConfig.class})
public class CrdGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrdGeneratorApplication.class, args);
    }

}
