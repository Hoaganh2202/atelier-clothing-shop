package com.shop.backend.product;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SizeComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Map<String, Integer> ALPHA_SIZES = Map.ofEntries(
            Map.entry("XXS", 10),
            Map.entry("XS", 20),
            Map.entry("S", 30),
            Map.entry("M", 40),
            Map.entry("L", 50),
            Map.entry("XL", 60),
            Map.entry("XXL", 70),
            Map.entry("2XL", 70),
            Map.entry("XXXL", 80),
            Map.entry("3XL", 80),
            Map.entry("XXXXL", 90),
            Map.entry("4XL", 90)
    );

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)$");

    @Override
    public int compare(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 0;
        }
        if (s1 == null || s1.trim().isEmpty()) {
            return (s2 == null || s2.trim().isEmpty()) ? 0 : 1;
        }
        if (s2 == null || s2.trim().isEmpty()) {
            return -1;
        }

        String t1 = s1.trim();
        String t2 = s2.trim();

        int group1 = getGroup(t1);
        int group2 = getGroup(t2);

        if (group1 != group2) {
            return Integer.compare(group1, group2);
        }

        if (group1 == 1) {
            int rank1 = ALPHA_SIZES.get(t1.toUpperCase());
            int rank2 = ALPHA_SIZES.get(t2.toUpperCase());
            return Integer.compare(rank1, rank2);
        }

        if (group1 == 2) {
            Matcher m1 = NUMERIC_PATTERN.matcher(t1);
            Matcher m2 = NUMERIC_PATTERN.matcher(t2);
            if (m1.matches() && m2.matches()) {
                double val1 = Double.parseDouble(m1.group(1));
                double val2 = Double.parseDouble(m2.group(1));
                int valCmp = Double.compare(val1, val2);
                if (valCmp != 0) {
                    return valCmp;
                }
                String unit1 = m1.group(2);
                String unit2 = m2.group(2);
                int unitCmp = unit1.compareToIgnoreCase(unit2);
                if (unitCmp != 0) {
                    return unitCmp;
                }
            }
            return t1.compareToIgnoreCase(t2);
        }

        return t1.compareToIgnoreCase(t2);
    }

    private int getGroup(String s) {
        if (ALPHA_SIZES.containsKey(s.toUpperCase())) {
            return 1;
        }
        if (NUMERIC_PATTERN.matcher(s).matches()) {
            return 2;
        }
        return 3;
    }
}
