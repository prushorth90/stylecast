package com.stylecast.occasion;

import com.stylecast.occasion.dto.OccasionInterpretationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for event occasion interpretation. All business rules
 * live in {@link OccasionInterpretationService}. This module never invokes
 * live product search or assembles outfits - it only classifies the
 * occasion.
 */
@RestController
@RequestMapping("/api/events/{eventId}/interpretation")
public class OccasionInterpretationController {

    private final OccasionInterpretationService interpretationService;

    public OccasionInterpretationController(OccasionInterpretationService interpretationService) {
        this.interpretationService = interpretationService;
    }

    @GetMapping
    public OccasionInterpretationResponse getInterpretation(@PathVariable UUID eventId) {
        return interpretationService.getInterpretation(eventId);
    }

    @PostMapping("/regenerate")
    public OccasionInterpretationResponse regenerateInterpretation(@PathVariable UUID eventId) {
        return interpretationService.regenerateInterpretation(eventId);
    }
}
