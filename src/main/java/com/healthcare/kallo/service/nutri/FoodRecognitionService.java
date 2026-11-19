package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.response.nutri.RecognizeFoodResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface FoodRecognitionService {

  RecognizeFoodResponse recognizeFood(UUID userId, MultipartFile file);
}
