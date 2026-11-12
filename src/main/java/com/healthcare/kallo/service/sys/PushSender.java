package com.healthcare.kallo.service.sys;

import java.util.List;

public interface PushSender {
  List<PushResult> send(PushMessage message, List<String> tokens);
}
