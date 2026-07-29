package com.stylecast.retail;

import com.stylecast.retail.dto.RetailProductCandidateResponse;
import com.stylecast.retail.dto.RetailProductSearchApiRequest;
import com.stylecast.retail.dto.RetailProductSearchApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Temporary development-only endpoint for exercising the live Nordstrom
 * product-search provider directly.
 *
 * <p>This is NOT a customer-facing search page and must not be linked from
 * normal user-facing flows. It exists so the provider can be verified (via
 * Swagger UI or curl) ahead of the recommendation engine calling
 * {@link RetailProductSearchService} automatically in a later task.
 */
@RestController
@RequestMapping("/api/dev/retail-products")
public class DevRetailProductSearchController {

    private final RetailProductSearchService searchService;

    public DevRetailProductSearchController(RetailProductSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public RetailProductSearchApiResponse search(@Valid @RequestBody RetailProductSearchApiRequest request) {
        RetailProductSearchRequest domainRequest = new RetailProductSearchRequest(
                request.retailer(),
                request.category(),
                request.keywords(),
                request.maxPrice(),
                request.clothingSize(),
                request.limit() == null ? 0 : request.limit());

        RetailProductSearchResult result = searchService.search(domainRequest);

        List<RetailProductCandidateResponse> candidates = result.candidates().stream()
                .map(RetailProductCandidateResponse::fromDomain)
                .toList();

        return new RetailProductSearchApiResponse(candidates);
    }
}
