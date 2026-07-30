package com.stylecast.event.styling;

/**
 * The department/gender a user wants outfit recommendations shopped from.
 * Required on every {@link EventStylePreferences} record - {@link
 * #NO_PREFERENCE} is the explicit "no restriction" choice (and the safe
 * default applied to preference rows saved before this field existed).
 */
public enum ShoppingDepartment {
    MEN,
    WOMEN,
    UNISEX,
    NO_PREFERENCE
}
