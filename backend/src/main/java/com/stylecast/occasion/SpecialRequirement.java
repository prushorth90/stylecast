package com.stylecast.occasion;

/**
 * A special styling requirement inferred for an event, produced by an
 * {@link OccasionClassifier}.
 *
 * <p>These may only be derived from explicit event text (title,
 * description, manually entered dress code, or outfit request) - never
 * from live weather data or an unstated model assumption about current
 * conditions. For example, {@link #RAIN_SUITABLE} may be set because an
 * event's description literally mentions rain, but never because a
 * weather forecast (from {@code com.stylecast.weather}) predicts rain.
 */
public enum SpecialRequirement {
    OUTDOOR_SUITABLE,
    RAIN_SUITABLE,
    HOT_WEATHER_SUITABLE,
    COLD_WEATHER_SUITABLE,
    GRASS_FRIENDLY_FOOTWEAR,
    COMFORTABLE_FOR_WALKING,
    NOT_OVERLY_FORMAL,
    LAYER_RECOMMENDED
}
