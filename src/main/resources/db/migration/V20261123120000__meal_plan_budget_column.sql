-- V20261123120000__meal_plan_budget_column.sql
-- Add max_budget_vnd column to meal_plans table

ALTER TABLE meal_plans ADD COLUMN IF NOT EXISTS max_budget_vnd INTEGER;
