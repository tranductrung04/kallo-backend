package com.healthcare.kallo.service.food;

import com.healthcare.kallo.dto.response.food.FoodResponse;

public interface BarcodeService {
  FoodResponse getFoodByBarcode(String barcode);
}
