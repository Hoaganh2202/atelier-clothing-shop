package com.shop.backend.product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SizeComparatorTest {

    private final SizeComparator comparator = new SizeComparator();

    @Test
    @DisplayName("Alpha sizes are sorted XXS through 4XL case-insensitively")
    void testStandardAlphaSizes() {
        List<String> sizes = new ArrayList<>(List.of("xl", "M", "xxs", "4XL", "S", "L", "xs", "3xl", "xxl"));
        sizes.sort(comparator);

        assertThat(sizes).containsExactly("xxs", "xs", "S", "M", "L", "xl", "xxl", "3xl", "4XL");
    }

    @Test
    @DisplayName("2XL and XXL have equal rank")
    void test2XLAndXXLEqualRank() {
        assertThat(comparator.compare("2XL", "XXL")).isZero();
        assertThat(comparator.compare("xxl", "2xl")).isZero();
        assertThat(comparator.compare("2xl", "XXL")).isZero();

        // Both rank after XL and before 3XL
        assertThat(comparator.compare("XL", "2XL")).isNegative();
        assertThat(comparator.compare("XL", "XXL")).isNegative();
        assertThat(comparator.compare("2XL", "3XL")).isNegative();
        assertThat(comparator.compare("XXL", "3XL")).isNegative();
    }

    @Test
    @DisplayName("Numeric sizes are sorted ascending by numeric value")
    void testNumericSizes() {
        List<String> sizes = new ArrayList<>(List.of("32", "29", "30", "31", "30.5"));
        sizes.sort(comparator);

        assertThat(sizes).containsExactly("29", "30", "30.5", "31", "32");
    }

    @Test
    @DisplayName("Numeric sizes with cm or unit suffixes are sorted by numeric value")
    void testNumericSizesWithUnitSuffix() {
        List<String> sizes = new ArrayList<>(List.of("120cm", "29", "115cm", "32"));
        sizes.sort(comparator);

        assertThat(sizes).containsExactly("29", "32", "115cm", "120cm");
    }

    @Test
    @DisplayName("Group precedence: Alpha sizes < Numeric sizes < Special/Other text")
    void testGroupPrecedence() {
        List<String> sizes = new ArrayList<>(List.of("Freesize", "30", "M", "N/A", "29", "XL", "115cm", "CustomSize"));
        sizes.sort(comparator);

        assertThat(sizes).containsExactly("M", "XL", "29", "30", "115cm", "CustomSize", "Freesize", "N/A");
    }

    @Test
    @DisplayName("Other text sizes are sorted alphabetically")
    void testOtherTextSizesAlphabetical() {
        List<String> sizes = new ArrayList<>(List.of("One Size", "Freesize", "N/A", "Custom", "Bespoke"));
        sizes.sort(comparator);

        assertThat(sizes).containsExactly("Bespoke", "Custom", "Freesize", "N/A", "One Size");
    }

    @Test
    @DisplayName("Null and empty/blank values are placed last")
    void testNullAndEmptyHandling() {
        List<String> sizes = new ArrayList<>(Arrays.asList("M", null, "", "  ", "S", "30"));
        sizes.sort(comparator);

        assertThat(sizes.subList(0, 3)).containsExactly("S", "M", "30");
        assertThat(sizes.subList(3, 6)).containsOnly(null, "", "  ");

        assertThat(comparator.compare(null, null)).isZero();
        assertThat(comparator.compare(null, "")).isZero();
        assertThat(comparator.compare(" ", "")).isZero();
    }
}
