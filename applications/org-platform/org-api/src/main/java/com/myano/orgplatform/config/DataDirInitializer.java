package com.myano.orgplatform.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataDirInitializer {

  private static final Logger log = LoggerFactory.getLogger(DataDirInitializer.class);

  private final OrgPlatformProperties properties;

  public DataDirInitializer(OrgPlatformProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void ensureDataDir() throws Exception {
    Path dir = Path.of(properties.getDataDir());
    Files.createDirectories(dir);
    log.info("org-platform data dir: {}", dir.toAbsolutePath());
  }
}
