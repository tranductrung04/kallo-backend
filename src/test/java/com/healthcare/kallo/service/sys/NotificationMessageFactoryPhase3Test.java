package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class NotificationMessageFactoryPhase3Test {
  private final DefaultNotificationMessageFactory factory = new DefaultNotificationMessageFactory(source());

  @Test
  void everyNotificationTypeHasViAndEnglishCenterAndPushTemplates() throws Exception {
    Properties vi = load("messages/notifications_vi.properties");
    Properties en = load("messages/notifications_en.properties");
    for (NotificationType type : NotificationType.values()) {
      String prefix = "notifications." + type.name().toLowerCase(Locale.ROOT);
      for (String suffix : new String[] {".title", ".body", ".push.title", ".push.body"}) {
        assertThat(vi).containsKey(prefix + suffix);
        assertThat(en).containsKey(prefix + suffix);
      }
    }
  }

  @Test
  void pushContentContainsNoCalorieValuesAndEnglishKeepsApostrophe() {
    var content = factory.render(
        NotificationType.CALORIE_OVER, Locale.ENGLISH, Map.of("caloriesIn", 2534.5, "thresholdPct", 100));
    assertThat(content.body()).contains("2,535").contains("today's");
    assertThat(content.pushBody()).doesNotContain("2534").doesNotContain("2,535").doesNotContain("100");
  }

  @Test
  void allPushTemplatesIgnoreHealthParameters() {
    for (NotificationType type : NotificationType.values()) {
      var content = factory.render(
          type, Locale.ENGLISH, Map.of("caloriesIn", 2534.5, "thresholdPct", 100, "message", "secret"));
      assertThat(content.pushTitle()).doesNotContain("2534").doesNotContain("100").doesNotContain("secret");
      assertThat(content.pushBody()).doesNotContain("2534").doesNotContain("100").doesNotContain("secret");
    }
  }

  private ResourceBundleMessageSource source() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasenames("messages/notifications");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  private Properties load(String resource) throws Exception {
    Properties properties = new Properties();
    try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      properties.load(stream);
    }
    return properties;
  }
}
