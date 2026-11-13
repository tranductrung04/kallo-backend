INSERT INTO badges (code, name, description, icon_key, rule_type, threshold, points, active, sort_order)
VALUES
  ('FIRST_LOG', 'Bước đầu tiên', 'Ghi nhật ký đầu tiên', 'first-log', 'FIRST_LOG', 1, 10, TRUE, 1),
  ('STREAK_7', 'Kiên trì 7 ngày', 'Hoàn thành mục tiêu 7 ngày liên tiếp', 'streak-7', 'STREAK_DAYS', 7, 20, TRUE, 2),
  ('STREAK_30', 'Kiên trì 30 ngày', 'Hoàn thành mục tiêu 30 ngày liên tiếp', 'streak-30', 'STREAK_DAYS', 30, 50, TRUE, 3),
  ('STREAK_100', 'Kiên trì 100 ngày', 'Hoàn thành mục tiêu 100 ngày liên tiếp', 'streak-100', 'STREAK_DAYS', 100, 150, TRUE, 4),
  ('FIRST_FRIEND', 'Người bạn đầu tiên', 'Kết nối với người bạn đầu tiên', 'first-friend', 'FRIENDS_COUNT', 1, 10, TRUE, 5),
  ('CHALLENGER_1', 'Người thử thách', 'Hoàn thành thử thách đầu tiên', 'challenger-1', 'CHALLENGES_COMPLETED', 1, 20, TRUE, 6),
  ('CHALLENGER_5', 'Bậc thầy thử thách', 'Hoàn thành 5 thử thách', 'challenger-5', 'CHALLENGES_COMPLETED', 5, 60, TRUE, 7),
  ('WEIGHT_GOAL_REACHED', 'Mục tiêu cân nặng', 'Đạt mục tiêu cân nặng', 'weight-goal-reached', 'WEIGHT_GOAL_REACHED', NULL, 100, FALSE, 8)
ON CONFLICT (code) DO NOTHING;
