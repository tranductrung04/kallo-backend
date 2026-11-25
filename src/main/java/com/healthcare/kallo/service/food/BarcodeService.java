package com.healthcare.kallo.service.food;

import com.healthcare.kallo.dto.response.food.FoodResponse;
import java.util.UUID;

public interface BarcodeService {
  FoodResponse getFoodByBarcode(UUID currentUserId, String barcode);
}
