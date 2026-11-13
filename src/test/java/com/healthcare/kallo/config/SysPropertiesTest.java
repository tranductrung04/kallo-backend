package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SysPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class)
          .withPropertyValues(
              "app.notification.reminder-catch-up=10m",
              "app.notification.retention-days=30",
              "app.notification.reminder-daily-cap=6",
              "app.notification.max-devices-per-user=10",
              "app.notification.device-retention-days=60",
              "app.sync.settle-delay=2s",
              "app.sync.tombstone-retention=30d",
              "app.sync.max-future-skew=5m",
              "app.sync.max-operations=200",
              "app.sync.default-page=500",
              "app.sync.max-page=1000",
              "app.attribution.open-food-facts-url=https://world.openfoodfacts.org",
              "app.attribution.open-food-facts-license=ODbL-1.0",
              "app.attribution.open-food-facts-attribution=Data from Open Food Facts.",
              "app.attribution.nin-url=https://viendinhduong.vn/",
              "app.attribution.nin-license=Vietnamese Food Composition Table terms",
              "app.attribution.nin-attribution=Data from NIN.",
              "app.attribution.fao-infoods-url=https://www.fao.org/infoods",
              "app.attribution.fao-infoods-license=FAO/INFOODS terms of use",
              "app.attribution.fao-infoods-attribution=Data from FAO/INFOODS.");

  @Test
  void pushDisabledContextStartsWithoutCredentials() {
    runner
        .withPropertyValues("app.push.enabled=false", "app.push.credentials-path=")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void enabledPushWithBlankCredentialsFailsBinding() {
    runner
        .withPropertyValues("app.push.enabled=true", "app.push.credentials-path=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void defaultPageCannotExceedMaxPage() {
    runner
        .withPropertyValues("app.push.enabled=false", "app.sync.default-page=1001")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void blankOpenFoodFactsAttributionFailsBinding() {
    runner
        .withPropertyValues("app.push.enabled=false", "app.attribution.open-food-facts-attribution=")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties({
    PushProperties.class,
    NotificationProperties.class,
    SyncProperties.class,
    AttributionProperties.class
  })
  static class PropertiesConfig {}
}
