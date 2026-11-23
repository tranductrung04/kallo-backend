package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;

public class ChatAiException extends BusinessException {
  public ChatAiException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ChatAiException(ErrorCode errorCode, Object details) {
    super(errorCode, details);
  }
}
