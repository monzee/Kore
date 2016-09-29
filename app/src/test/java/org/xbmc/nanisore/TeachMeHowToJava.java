package org.xbmc.nanisore;

import org.junit.Test;

import static org.junit.Assert.*;

public class TeachMeHowToJava {

    @Test
    public void it_is_ok_to_call_equals_with_null() {
        assertFalse("foo".equals(null));
    }

    @Test
    public void string_isEmpty_is_for_length_checking_only() {
        assertFalse(" ".isEmpty());
        assertTrue("".isEmpty());
        assertTrue("       ".trim().isEmpty());
    }

    @Test
    public void split_will_return_a_singleton_array_if_the_delimiter_is_absent() {
        String s = "abc def ghi jkl mno";
        String[] parts = s.split("#");
        assertEquals(1, parts.length);
        assertEquals(s, parts[0]);
    }

    @Test
    public void it_is_ok_to_split_an_empty_substring() {
        String p = "/";
        String[] parts = p.substring(1).split("/", 2);
        assertEquals(1, parts.length);
        assertTrue(parts[0].isEmpty());
    }

}
