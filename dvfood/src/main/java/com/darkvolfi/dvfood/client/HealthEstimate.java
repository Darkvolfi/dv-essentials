package com.darkvolfi.dvfood.client;

import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;

/**
 * Food heals only through natural regeneration, so this replays the regeneration
 * rules of FoodData.tick to estimate how much health eating would add.
 */
final class HealthEstimate {
    private static final int MAX_TICKS = 20 * 60 * 10;

    private HealthEstimate() {}

    /** Extra health from eating {@code food} compared with not eating it. */
    static float healthFromEating(FoodData data, FoodProperties food, float health, float maxHealth) {
        int newFood = Math.min(20, data.getFoodLevel() + food.getNutrition());
        float newSaturation = Math.min(newFood, data.getSaturationLevel() + FoodHud.saturationGain(food));
        float after = regenerate(newFood, newSaturation, data.getExhaustionLevel(), maxHealth - health);
        float before = regenerate(data.getFoodLevel(), data.getSaturationLevel(), data.getExhaustionLevel(), maxHealth - health);
        return Math.max(0, after - before);
    }

    private static float regenerate(int food, float saturation, float exhaustion, float missing) {
        float healed = 0;
        int timer = 0;
        for (int tick = 0; tick < MAX_TICKS && healed < missing; tick++) {
            if (exhaustion > 4.0f) {
                exhaustion -= 4.0f;
                if (saturation > 0) saturation = Math.max(saturation - 1.0f, 0);
                else food = Math.max(food - 1, 0);
            }
            if (saturation > 0 && food >= 20) {
                if (++timer >= 10) {
                    float spent = Math.min(saturation, 6.0f);
                    healed += spent / 6.0f;
                    exhaustion = Math.min(exhaustion + spent, 40.0f);
                    timer = 0;
                }
            } else if (food >= 18) {
                if (++timer >= 80) {
                    healed += 1.0f;
                    exhaustion = Math.min(exhaustion + 6.0f, 40.0f);
                    timer = 0;
                }
            } else {
                break; // no natural regeneration below 18 food
            }
        }
        return Math.min(healed, missing);
    }
}
