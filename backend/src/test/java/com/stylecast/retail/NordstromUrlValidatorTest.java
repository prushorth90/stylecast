package com.stylecast.retail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NordstromUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.nordstrom.com/s/mens-suit-jacket/6819331",
            "https://nordstrom.com/s/mens-suit-jacket/6819331",
            "https://shop.nordstrom.com/s/navy-wedding-suit/1234567",
            "https://www.nordstrom.com/s/mens-suit-jacket/6819331/",
    })
    void isValidNordstromProductUrl_acceptsRealProductPageUrls(String url) {
        assertThat(NordstromUrlValidator.isValidNordstromProductUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.nordstrom.com/s/mens-suit-jacket/6819331", // not https
            "https://www.nordstrom.com/", // bare homepage
            "https://www.nordstrom.com/browse/men/clothing/suits", // category/browse page
            "https://www.nordstrom.com/sr?origin=keywordsearch&keyword=suit", // search results page
            "https://www.nordstromrack.com/s/mens-suit-jacket/6819331", // different (unrelated) site
            "https://evilnordstrom.com/s/mens-suit-jacket/6819331", // lookalike domain
            "https://nordstrom.com.evil.com/s/mens-suit-jacket/6819331", // domain suffix trick
            "https://www.nordstrom.com/s/mens-suit-jacket", // missing numeric product id
            "not a url",
            "",
    })
    void isValidNordstromProductUrl_rejectsNonProductOrUnrelatedUrls(String url) {
        assertThat(NordstromUrlValidator.isValidNordstromProductUrl(url)).isFalse();
    }

    @Test
    void isValidNordstromProductUrl_rejectsNull() {
        assertThat(NordstromUrlValidator.isValidNordstromProductUrl(null)).isFalse();
    }

    @Test
    void canonicalize_stripsQueryFragmentTrailingSlashAndLowercasesHostAndScheme() {
        String canonical = NordstromUrlValidator.canonicalize(
                "https://WWW.Nordstrom.com/s/mens-suit-jacket/6819331/?color=navy#details");

        assertThat(canonical).isEqualTo("https://www.nordstrom.com/s/mens-suit-jacket/6819331");
    }

    @Test
    void canonicalize_ofEquivalentUrls_producesSameKey() {
        String a = NordstromUrlValidator.canonicalize("https://www.nordstrom.com/s/mens-suit-jacket/6819331");
        String b = NordstromUrlValidator.canonicalize("https://www.nordstrom.com/s/mens-suit-jacket/6819331/?color=navy");

        assertThat(a).isEqualTo(b);
    }
}
