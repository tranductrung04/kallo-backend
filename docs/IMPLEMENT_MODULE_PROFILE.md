# HƯỚNG DẪN TRIỂN KHAI KỸ THUẬT: MODULE PROFILE

---

## 1. TỔNG QUAN KIẾN TRÚC & MỤC TIÊU TRIỂN KHAI

### 1.1 Mục tiêu
Module `PROFILE` quản lý hồ sơ sinh học, chỉ số cơ thể, mục tiêu cân nặng, danh sách bệnh mạn tính và dị ứng nguyên liệu của người dùng. Module chịu trách nhiệm tính toán nhu cầu năng lượng ($\text{TDEE}$, $\text{BMR}$), phân bổ dinh dưỡng ($\text{Macro}$, $\text{Water}$) và phát tín hiệu cho các module khác (`ACT`, `PLAN`, `NUTRI`).

### 1.2 Quyết định kiến trúc chính
1. **Mô hình Domain-Driven Design (DDD) & Ports & Adapters:**
   - Core domain chứa toàn bộ logic tính toán chỉ số, quy tắc an toàn y sinh và kiểm tra ràng buộc.
   - Không phụ thuộc trực tiếp vào DB framework hay các module khác. Giao tiếp qua Interfaces/Ports.
2. **Phát triển Greenfield (Rút gọn Lộ trình Migration):**
   - Hệ thống phát triển mới, không cần các phase chuyển tiếp Expand-Contract phức tạp, không tạo bảng backfill tạm hay dùng extension `unaccent`.
   - Tạo trực tiếp các bảng chuẩn hóa (`user_chronic_conditions`, `user_allergies`, `chronic_conditions`) và loại bỏ hoàn toàn các cột JSONB cũ ngay từ bản DDL khởi tạo.
3. **Đồng bộ sự kiện không bất đồng bộ trực tiếp (Event-Driven & Ports):**
   - Sự kiện thay đổi cân nặng từ module `ACT` (`WeightChangedEvent`) truyền trực tiếp giá trị cân nặng mới nhất trong Payload để loại bỏ phụ thuộc vòng (Circular Dependency).
   - Khi tạo/cập nhật cân nặng chính thức ở `PROFILE`, hệ thống tự phát sự kiện `ProfileUpdatedEvent` hoặc ghi nhận cân nặng ban đầu sang `ACT` để đồng nhất dữ liệu hiển thị trên Dashboard.

---

## 2. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

### 2.1 Sơ đồ ERD & Cấu trúc Bảng

```
+------------------------------------+           +----------------------------------+
|        user_health_profiles        |           |        chronic_conditions        |
+------------------------------------+           +----------------------------------+
| PK user_id                UUID     |           | PK id                   UUID     |
|    gender                 VARCHAR  |           |    code                 VARCHAR  |
|    dob                    DATE     |           |    name                 VARCHAR  |
|    height_cm              NUMERIC  |           |    description          TEXT     |
|    weight_kg              NUMERIC  |           |    is_active            BOOLEAN  |
|    activity_level         VARCHAR  |           +----------------------------------+
|    goal_type              VARCHAR  |                            ^
|    target_weight_kg       NUMERIC  |                            | (1:N)
|    weekly_rate_kg         NUMERIC  |           +----------------------------------+
|    bmr                    NUMERIC  |           |     user_chronic_conditions      |
|    tdee                   NUMERIC  |           +----------------------------------+
|    daily_calorie_goal     NUMERIC  |           | PK user_id                UUID   |
|    carb_grams             NUMERIC  |           | PK condition_id           UUID   |
|    protein_grams          NUMERIC  |           |    diagnosed_at            DATE   |
|    fat_grams              NUMERIC  |           |    note                 TEXT     |
|    water_liters           NUMERIC  |           +----------------------------------+
|    recalc_recommended     BOOLEAN  |
|    recommended_tdee       NUMERIC  |           +----------------------------------+
|    recommended_goal_kcal  NUMERIC  |           |          user_allergies           |
|    latest_logged_weight   NUMERIC  |           +----------------------------------+
|    rate_clamped           BOOLEAN  |           | PK user_id                UUID   |
|    version                BIGINT   |           | PK ingredient_id          UUID   |
|    created_at             TIMESTAMPTZ|         |    note                 TEXT     |
|    updated_at             TIMESTAMPTZ|         +----------------------------------+
+------------------------------------+
```

### 2.2 Script Migration SQL (`V1__init_profile_tables.sql`)

```sql
-- 1. Bảng hồ sơ sức khỏe người dùng
CREATE TABLE user_health_profiles (
    user_id UUID PRIMARY KEY,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    dob DATE NOT NULL,
    height_cm NUMERIC(5, 2) NOT NULL CHECK (height_cm BETWEEN 50.0 AND 250.0),
    weight_kg NUMERIC(5, 2) NOT NULL CHECK (weight_kg BETWEEN 20.0 AND 300.0),
    activity_level VARCHAR(20) NOT NULL CHECK (activity_level IN ('SEDENTARY', 'LIGHTLY_ACTIVE', 'MODERATELY_ACTIVE', 'VERY_ACTIVE', 'EXTRA_ACTIVE')),
    goal_type VARCHAR(20) NOT NULL CHECK (goal_type IN ('LOSE_WEIGHT', 'MAINTAIN_WEIGHT', 'GAIN_WEIGHT')),
    target_weight_kg NUMERIC(5, 2) CHECK (target_weight_kg IS NULL OR (target_weight_kg BETWEEN 20.0 AND 300.0)),
    weekly_rate_kg NUMERIC(3, 2) NOT NULL DEFAULT 0.00 CHECK (weekly_rate_kg BETWEEN 0.00 AND 1.50),
    bmr NUMERIC(6, 2) NOT NULL CHECK (bmr > 0),
    tdee NUMERIC(6, 2) NOT NULL CHECK (tdee > 0),
    daily_calorie_goal NUMERIC(6, 2) NOT NULL CHECK (daily_calorie_goal > 0),
    carb_grams NUMERIC(6, 2) NOT NULL CHECK (carb_grams >= 0),
    protein_grams NUMERIC(6, 2) NOT NULL CHECK (protein_grams >= 0),
    fat_grams NUMERIC(6, 2) NOT NULL CHECK (fat_grams >= 0),
    water_liters NUMERIC(4, 2) NOT NULL CHECK (water_liters >= 0),
    recalculation_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_tdee NUMERIC(6, 2),
    recommended_daily_calorie_goal NUMERIC(6, 2),
    latest_logged_weight_kg NUMERIC(5, 2),
    rate_clamped BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_target_weight_required CHECK (
        (goal_type = 'MAINTAIN_WEIGHT' AND target_weight_kg IS NULL AND weekly_rate_kg = 0.00) OR
        (goal_type IN ('LOSE_WEIGHT', 'GAIN_WEIGHT') AND target_weight_kg IS NOT NULL AND weekly_rate_kg > 0.00)
    )
);

-- 2. Bảng danh mục bệnh mạn tính
CREATE TABLE chronic_conditions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng liên kết bệnh mạn tính người dùng
CREATE TABLE user_chronic_conditions (
    user_id UUID NOT NULL REFERENCES user_health_profiles(user_id) ON DELETE CASCADE,
    condition_id UUID NOT NULL REFERENCES chronic_conditions(id),
    diagnosed_at DATE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, condition_id)
);

-- 4. Bảng dị ứng nguyên liệu của người dùng
CREATE TABLE user_allergies (
    user_id UUID NOT NULL REFERENCES user_health_profiles(user_id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL, -- FK logic tới bảng ingredients của module FOODDATA
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, ingredient_id)
);

CREATE INDEX idx_user_allergies_user_id ON user_allergies(user_id);
CREATE INDEX idx_user_chronic_user_id ON user_chronic_conditions(user_id);
```

---

## 3. QÚY TẮC NGHIỆP VỤ & CÔNG THỨC TINH TOÁN DOMAIN

### 3.1 Tính Tuổi và BMR (Mifflin-St Jeor)
Tuổi được tính theo số năm tròn tại thời điểm tính toán dựa trên `Clock` cấu hình múi giờ `Asia/Ho_Chi_Minh`.

Công thức Mifflin-St Jeor:
$$BMR = 10 \times W + 6.25 \times H - 5 \times A + s$$

Trong đó:
- $W$: Cân nặng ($\text{kg}$)
- $H$: Chiều cao ($\text{cm}$)
- $A$: Tuổi tròn ($\text{tuổi}$)
- $s$: Hệ số giới tính ($s = +5$ cho Nam `MALE`, $s = -161$ cho Nữ `FEMALE`).

### 3.2 Tính TDEE (Total Daily Energy Expenditure)
$$TDEE = BMR \times PAL$$

Hệ số mức độ vận động ($PAL$):
- `SEDENTARY`: $1.2$
- `LIGHTLY_ACTIVE`: $1.375$
- `MODERATELY_ACTIVE`: $1.55$
- `VERY_ACTIVE`: $1.725$
- `EXTRA_ACTIVE`: $1.9$

### 3.3 Tính Calo Mục Tiêu Dựa Trên Goal Type & Kiểm Soát An Toàn

#### 1. Đổi biến tốc độ sang Calo:
Thay đổi $1\text{ kg}$ mỡ tương đương khoảng $7700\text{ kcal}$.
$$\Delta_{\text{daily}} = \frac{\text{weekly\_rate\_kg} \times 7700}{7}$$

#### 2. Giới hạn tốc độ theo Cân nặng (Max $1\%$ Cân nặng / tuần):
$$\text{max\_rate\_allowed} = \text{ROUND}(W \times 0.01, 2)$$
Nếu $\text{weekly\_rate\_kg} > \text{max\_rate\_allowed}$:
- Ghi nhận `rate_clamped = true`.
- Áp dụng $\text{effective\_rate} = \text{max\_rate\_allowed}$ để tính calo.

#### 3. Điều chỉnh Calo theo Mục tiêu:
- `MAINTAIN_WEIGHT`: 
  $$\text{daily\_calorie\_goal} = TDEE$$
- `LOSE_WEIGHT`:
  $$\text{daily\_calorie\_goal\_raw} = TDEE - \Delta_{\text{daily}}$$
  **Sàn Calo An Toàn ($\text{MinSafe}$):** Nam $1500\text{ kcal/ngày}$, Nữ $1200\text{ kcal/ngày}$.
  - **Xử lý Đặc biệt khi $TDEE \le \text{MinSafe}$:** Nếu $TDEE \le \text{MinSafe}$, việc cố áp sàn $\text{MinSafe}$ sẽ làm mục tiêu giảm cân biến thành tăng cân ($TDEE - \Delta_{\text{daily}} < TDEE \le \text{MinSafe}$). Trong trường hợp này, **không được áp sàn vượt quá $TDEE$**. Đặt $\text{daily\_calorie\_goal} = \text{MAX}(TDEE - \Delta_{\text{daily}}, BMR)$. Nếu $TDEE - \Delta_{\text{daily}} < BMR$, phát cảnh báo yêu cầu người dùng tăng vận động thay vì nhịn ăn quá mức.
  - Trường hợp $TDEE > \text{MinSafe}$: 
    $$\text{daily\_calorie\_goal} = \text{MAX}(\text{daily\_calorie\_goal\_raw}, \text{MinSafe})$$
- `GAIN_WEIGHT`:
  $$\text{daily\_calorie\_goal} = TDEE + \Delta_{\text{daily}}$$

### 3.4 Phân Bổ Macro & Lượng Nước
- **Protein:** $2.0\text{ g/kg}$ cân nặng ($1\text{ g protein} = 4\text{ kcal}$).
- **Fat:** $25\%$ tổng calo mục tiêu ($1\text{ g fat} = 9\text{ kcal}$).
  $$\text{fat\_grams} = \frac{\text{daily\_calorie\_goal} \times 0.25}{9}$$
- **Carbohydrate:** Năng lượng còn lại ($1\text{ g carb} = 4\text{ kcal}$).
  $$\text{carb\_kcal} = \text{daily\_calorie\_goal} - (\text{protein\_grams} \times 4 + \text{fat\_grams} \times 9)$$
  $$\text{carb\_grams} = \text{MAX}\left(0, \frac{\text{carb\_kcal}}{4}\right)$$
- **Nước Khuyên Dùng:**
  $$\text{water\_liters} = \text{ROUND}\left(W \times 0.035, 2\right)$$

### 3.5 Các Quy Tắc Chặn An Toàn Y Sinh (Biomedical Rules)

1. **Tuổi < 18:** Chỉ cho phép chọn `MAINTAIN_WEIGHT`. Từ chối `LOSE_WEIGHT` hoặc `GAIN_WEIGHT` (Mã lỗi: `UNDERAGE_GOAL_RESTRICTION`).
2. **Kiểm tra BMI An Toàn:**
   $$\text{BMI} = \frac{W}{(H/100)^2}$$
   - Nếu $\text{BMI} < 18.5$ (Thiếu cân): Từ chối mục tiêu `LOSE_WEIGHT` (Mã lỗi: `UNSAFE_TARGET_BMI`).
   - Mục tiêu cân nặng $\text{target\_weight\_kg}$ phải đảm bảo $\text{BMI}_{\text{target}} \ge 18.5$ đối với `LOSE_WEIGHT` và $\text{BMI}_{\text{target}} \le 35.0$ đối với `GAIN_WEIGHT`.
   - Tính toán $\text{minAllowedTargetKg}$ cho giảm cân:
     $$\text{minAllowedTargetKg} = \text{ROUND\_UP}\left(18.5 \times (H/100)^2, 1\right)$$
3. **Chạm/Đạt Mục tiêu Cân nặng khi `/recalculate`:**
   - Khi người dùng đang ở `LOSE_WEIGHT` mà cân nặng hiện tại $W \le \text{target\_weight\_kg}$, hoặc đang `GAIN_WEIGHT` mà $W \ge \text{target\_weight\_kg}$:
   - Hệ thống tự động chuyển mục tiêu về `MAINTAIN_WEIGHT`, gán $\text{target\_weight\_kg} = \text{null}$, $\text{weekly\_rate\_kg} = 0.00$, tính toán lại chỉ số theo `MAINTAIN_WEIGHT` và cập nhật chính thức vào DB. Trả về cờ `goalAdvice = "TARGET_REACHED"`.

---

## 4. TƯƠNG TÁC GIỮA CÁC MODULE (PORTS & EVENTS)

### 4.1 Tách Phụ Thuộc Vòng (PROFILE ↔ ACT)

Để tránh phụ thuộc vòng giữa module `PROFILE` và `ACT`:
1. `WeightChangedEvent` phát từ `ACT` **bắt buộc chứa sẵn giá trị cân nặng mới nhất** trong Event Payload.
2. `PROFILE` tiếp nhận event và không cần gọi ngược lại `ACT` Repository.

```java
// Payload Event từ ACT Module
public record WeightChangedEvent(
    UUID userId,
    BigDecimal weightKg,
    Instant loggedAt
) {}
```

### 4.2 Cập Nhật Cân Nặng 2 Chiều & Hành Vi Dashboard

1. **Khi ghi log cân nặng hằng ngày bên ACT (`WeightChangedEvent`):**
   - Listener bên PROFILE tính ngầm $\text{TDEE}_{\text{new}}$ dựa trên `weightKg` mới.
   - Nếu $|\text{TDEE}_{\text{new}} - \text{TDEE}_{\text{current}}| \ge 50\text{ kcal}$:
     - Bật cờ `recalculation_recommended = true`.
     - Lưu $\text{TDEE}_{\text{new}}$ vào `recommended_tdee` và tính `recommended_daily_calorie_goal`.
     - **Không tự động đổi `daily_calorie_goal` hiện tại.** Dashboard tiếp tục hiển thị calo mục tiêu cũ kèm một banner gợi ý người dùng cập nhật.
   - Nếu $|\text{TDEE}_{\text{new}} - \text{TDEE}_{\text{current}}| < 50\text{ kcal}$:
     - Nếu cờ `recalculation_recommended` đang bật mà cân nặng mới làm mức lệch giảm xuống dưới $50\text{ kcal}$, tự động tắt cờ `recalculation_recommended = false`.
2. **Khi xóa toàn bộ log cân nặng bên ACT:**
   - Nếu nhận event xóa cân nặng và người dùng không còn log cân nặng nào, tự động xóa các trường `recommended_*` và tắt cờ `recalculation_recommended`.
3. **Khi sửa cân nặng trực tiếp tại Profile (`PUT /users/me/profile`) hoặc Xác nhận tính lại (`POST /recalculate`):**
   - Đổi trực tiếp `weight_kg` và `daily_calorie_goal` trong DB.
   - Phát sự kiện `ProfileUpdatedEvent` để `ACT` đồng bộ bản ghi cân nặng khởi điểm/mới nhất nếu cần.

### 4.3 Quản Lý Dị Ứng Nguyên Liệu (`IngredientLookup`)

Module PROFILE tra cứu danh mục nguyên liệu qua Port `IngredientLookup` (do FOODDATA adapter implement):

```java
public interface IngredientLookup {
    Map<UUID, IngredientSummary> findSummariesByIds(Set<UUID> ids);
    List<IngredientSummary> findCommonAllergens(); // Trả về 8-10 loại dị ứng phổ biến (is_allergen = true)
}

public record IngredientSummary(
    UUID id,
    String name,
    boolean active
) {}
```

**Quy tắc kiểm tra active:**
- Khi `GET /users/me/allergies`: Hiển thị tất cả nguyên liệu đã lưu (kể cả nguyên liệu bị ẩn `active = false` trong danh mục FOODDATA).
- Khi `PUT /users/me/allergies`: 
  - Nếu `wanted.isEmpty()`: Xóa sạch danh sách dị ứng, không gọi DB lookup.
  - Chỉ chặn lỗi `INVALID_INGREDIENT` đối với các `ingredientId` **mới thêm vào** có `active = false`. Các nguyên liệu đã tồn tại từ trước trong danh sách dị ứng của user vẫn giữ nguyên.

---

## 5. THIẾT KẾ API ENDPOINTS & DTO S

### 5.1 Danh Sách Endpoints

| HTTP Method | URI Path | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/v1/users/me/profile` | Lấy thông tin hồ sơ sức khỏe & chỉ số hiện tại |
| `PUT` | `/api/v1/users/me/profile` | Tạo mới hoặc cập nhật toàn bộ hồ sơ sức khỏe |
| `POST` | `/api/v1/users/me/profile/recalculate` | Xác nhận tính toán lại hồ sơ từ cân nặng gợi ý |
| `GET` | `/api/v1/chronic-conditions` | Lấy danh mục bệnh mạn tính hệ thống |
| `GET` | `/api/v1/users/me/chronic-conditions` | Lấy danh sách bệnh mạn tính của người dùng |
| `PUT` | `/api/v1/users/me/chronic-conditions` | Cập nhật danh sách bệnh mạn tính của người dùng |
| `GET` | `/api/v1/users/me/allergies` | Lấy danh sách dị ứng nguyên liệu của người dùng |
| `PUT` | `/api/v1/users/me/allergies` | Cập nhật danh sách dị ứng nguyên liệu của người dùng |

### 5.2 Chi Tiết DTO Request/Response

#### `PUT /api/v1/users/me/profile`
```json
// Request Payload
{
  "gender": "FEMALE",
  "dob": "1995-08-15",
  "heightCm": 162.5,
  "weightKg": 58.0,
  "activityLevel": "MODERATELY_ACTIVE",
  "goalType": "LOSE_WEIGHT",
  "targetWeightKg": 53.0,
  "weeklyRateKg": 0.50
}
```

*Lưu ý:* Khi `goalType = "MAINTAIN_WEIGHT"`, trường `targetWeightKg` và `weeklyRateKg` có thể gửi `null`. Domain tự điều chỉnh `weeklyRateKg = 0.00`.

```json
// Response Payload (200 OK)
{
  "userId": "c0a80101-8c7a-1d2e-818c-7a1d2e000000",
  "gender": "FEMALE",
  "dob": "1995-08-15",
  "age": 31,
  "heightCm": 162.5,
  "weightKg": 58.0,
  "bmi": 22.0,
  "activityLevel": "MODERATELY_ACTIVE",
  "goalType": "LOSE_WEIGHT",
  "targetWeightKg": 53.0,
  "weeklyRateKg": 0.50,
  "bmr": 1317.88,
  "tdee": 2042.71,
  "dailyCalorieGoal": 1492.71,
  "macronutrients": {
    "proteinGrams": 116.0,
    "fatGrams": 41.46,
    "carbGrams": 163.89
  },
  "waterLiters": 2.03,
  "rateClamped": false,
  "recalculationRecommended": false,
  "version": 1
}
```

---

## 6. XỬ LÝ ĐỒNG THỜI & TRANSACTION SAFETY

### 6.1 Đua Tạo Hồ Sơ (Concurrency Onboarding)
Để tránh 2 request `PUT /profile` chạy đồng thời tạo ra 2 bản ghi trùng `user_id`:
1. Bảng `user_health_profiles` dùng `user_id` làm Primary Key.
2. Tách lớp `ProfileFacade` không có `@Transactional`.
3. Bắt `DataIntegrityViolationException` tại Facade và fallback chuyển sang luồng Update.

```java
@Component
@RequiredArgsConstructor
public class ProfileFacade {
    private final ProfileService profileService;

    public ProfileResponseDto upsertProfile(UUID userId, ProfileRequestDto request) {
        try {
            return profileService.createProfile(userId, request);
        } catch (DataIntegrityViolationException ex) {
            // Xảy ra khi dính trùng PK do 2 request tạo cùng lúc
            return profileService.updateProfile(userId, request);
        }
    }
}
```

### 6.2 Bảo Vệ Cập Nhật Với Lock Khóa Lạc Quan (Optimistic Locking)
- Entity `HealthProfileEntity` sử dụng thuộc tính `@Version private Long version;`.
- Khi cập nhật hồ sơ, JPA kiểm tra phiên bản. Nếu dính xung đột ghi (Concurrent Update), Spring ném `ObjectOptimisticLockingFailureException`.
- Xử lý EventListener recalculate: Sử dụng retry cơ chế Exponential Backoff (tối đa 3 lần) ở tầng Listener (nằm ngoài `@Transactional`).

---

## 7. BỘ TEST VECTORS & KỊCH BẢN KIỂM THỬ (TEST SUITE)

### 7.1 Bộ Test Vectors Tính Toán Domain

| Mã Vector | Giới tính | Ngày sinh (Tuổi) | Chiều cao | Cân nặng | Vận động | Goal Type | Target W | Weekly Rate | BMR | TDEE | Calo Goal | Rate Clamped | Kết quả mong đợi / Ghi chú |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **V1** | MALE | 1990-01-01 (36) | 175 cm | 70 kg | SEDENTARY | MAINTAIN | null | null | 1588.75 | 1906.50 | 1906.50 | false | Chuẩn duy trì cân nặng |
| **V2** | FEMALE | 1998-05-10 (28) | 160 cm | 55 kg | LIGHTLY | LOSE | 50 kg | 0.5 kg | 1254.00 | 1724.25 | 1200.00 | false | Chạm sàn Calo Nữ ($1200\text{ kcal}$) |
| **V3** | MALE | 2000-10-20 (26) | 180 cm | 85 kg | VERY | LOSE | 75 kg | 1.2 kg | 1845.00 | 3182.63 | 2247.63 | true | Kẹp tốc độ: $85\text{ kg} \times 1\% = 0.85\text{ kg/tuần}$ |
| **V4** | FEMALE | 2010-01-01 (16) | 155 cm | 48 kg | SEDENTARY | LOSE | 45 kg | 0.25 kg | - | - | - | - | **Lỗi:** `UNDERAGE_GOAL_RESTRICTION` (<18 tuổi) |
| **V5** | MALE | 1995-03-15 (31) | 170 cm | 52 kg | MODERATE | LOSE | 48 kg | 0.25 kg | - | - | - | - | **Lỗi:** `UNSAFE_TARGET_BMI` ($\text{BMI} = 17.99 < 18.5$) |
| **V6** | FEMALE | 1961-01-01 (65) | 148 cm | 46 kg | SEDENTARY | LOSE | 43 kg | 0.25 kg | 899.00 | 1078.80 | 899.00 | false | $TDEE \le 1200$. Áp $BMR$ làm sàn, không bị đẩy lên $1200$ |
| **V7** | MALE | 1992-07-04 (34) | 175 cm | 68 kg | EXTRA | GAIN | 73 kg | 0.5 kg | 1608.75 | 3056.63 | 3606.63 | false | Tăng cân chuẩn |
| **V8** | FEMALE | 1993-12-12 (33) | 165 cm | 60 kg | MODERATE | LOSE | 60 kg | 0.5 kg | - | - | - | false | **Tự chuyển MAINTAIN:** Cân nặng đã đạt mục tiêu |
| **V9** | MALE | 1988-11-30 (38) | 182 cm | 90 kg | MODERATE | LOSE | 80 kg | 0.5 kg | 1877.50 | 2910.13 | 2360.13 | false | Giảm cân chuẩn |

### 7.2 Danh Sách Error Codes

```java
public enum ProfileErrorCode {
    PROFILE_NOT_FOUND("PRF_404", "Hồ sơ người dùng không tồn tại"),
    UNDERAGE_GOAL_RESTRICTION("PRF_400_1", "Người dưới 18 tuổi chỉ được chọn giữ cân"),
    UNSAFE_TARGET_BMI("PRF_400_2", "Mục tiêu cân nặng khiến BMI nhỏ hơn 18.5"),
    INVALID_WEEKLY_RATE("PRF_400_3", "Tốc độ thay đổi cân nặng không hợp lệ"),
    INVALID_INGREDIENT("PRF_400_4", "Nguyên liệu dị ứng không tồn tại hoặc đã bị vô hiệu hóa"),
    INVALID_CONDITION("PRF_400_5", "Bệnh mạn tính không tồn tại trong hệ thống");

    private final String code;
    private final String message;
}
```

---

## 8. DANH SÁCH CHECKLIST TRƯỚC KHI GIAO DOANH CODE

- [ ] Script DB SQL đã chạy thành công và không chứa bất kỳ cột JSONB cũ nào.
- [ ] Mọi công thức $BMR$, $TDEE$, $\text{Macro}$, $\text{Water}$ đã khớp chính sở kiểm thử Unit Test với sai số $\le 0.01$.
- [ ] Đã viết Unit Test bao phủ 9 Test Vectors ở Mục 7.1.
- [ ] Luồng Event `WeightChangedEvent` nhận payload cân nặng trực tiếp, không gọi phụ thuộc ngược sang DB `ACT`.
- [ ] Luồng `POST /recalculate` tự động cập nhật `goal_type = MAINTAIN_WEIGHT` khi chạm mục tiêu.