package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class ChatSafetyService {
  public String validateAndNormalizeInput(String content, int maxLength) {
    String normalized = content == null ? "" : content.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    if (normalized.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    if (normalized.codePointCount(0, normalized.length()) > maxLength) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    return normalized;
  }

  public String validateOutput(String content) {
    if (content == null || content.isBlank()) throw new BusinessException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    return content.trim();
  }

  public String validateAndNormalizeTitle(String title) {
    String normalized = title == null ? "" : title.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
        .replaceAll("[\\r\\n\\t]+", " ");
    if (normalized.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    return normalized.codePoints().limit(120).collect(StringBuilder::new,
        StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }
}
