package com.stylecast.recommendation;

import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.occasion.RequestedItem;

import java.util.UUID;

/**
 * Lightweight, denormalized summary of one {@link RequestedItem} used only
 * for the live-recommendation "found"/"missing" display lists ({@code
 * foundRequestedItems}/{@code missingRequestedItems}) - deliberately omits
 * {@code searchTerms}/{@code required}/{@code displayOrder}, which are not
 * needed for that display and stay fully preserved at the occasion
 * interpretation level instead (see {@code OccasionRequestedItem}).
 */
public record RequestedItemSummary(UUID id, String originalPhrase, GenericItemCategory genericCategory, String activityContext) {

    public static RequestedItemSummary from(RequestedItem item) {
        return new RequestedItemSummary(item.id(), item.originalPhrase(), item.genericCategory(), item.activityContext());
    }
}
