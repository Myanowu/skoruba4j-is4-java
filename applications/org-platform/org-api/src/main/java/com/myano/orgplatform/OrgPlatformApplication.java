package com.myano.orgplatform;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrgPlatformApplication {

  public static void main(String[] args) throws Exception {
    String dataDir = System.getenv().getOrDefault("ORGPLATFORM_DATA_DIR", "./data");
    Files.createDirectories(Path.of(dataDir));
    SpringApplication.run(OrgPlatformApplication.class, args);
  }
}
