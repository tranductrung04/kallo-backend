package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.response.nutri.FoodRecognitionResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Service handling image validation, temporary upload, AI food recognition, and food mapping. */
public interface FoodRecognitionService {

  FoodRecognitionResponse recognize(UUID userId, MultipartFile image);
}
