package org.xbmc.nanisore.utils;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class UrlParsingTest {

    @Test
    public void path_starts_with_a_slash() throws MalformedURLException {
        String url = "https://example.com/foo/bar/baz";
        String path = new URL(url).getPath();
        assertThat(path, startsWith("/"));
    }

    private final Pattern getQueryVar = Pattern.compile("(?:^|&)v=([^&]+)");

    private void matchUrl(String url) throws MalformedURLException {
        String q = new URL(url).getQuery();
        assertNotNull(q);
        Matcher matcher = getQueryVar.matcher(q);
        assertTrue(matcher.find());
        assertEquals("abcdef", matcher.group(1));
    }

    @Test
    public void target_var_alone() throws MalformedURLException {
        matchUrl("http://youtu.be?v=abcdef");
    }

    @Test
    public void target_var_at_the_beginning() throws MalformedURLException {
        matchUrl("http://youtu.be?v=abcdef&a=123&x=poiw");
    }

    @Test
    public void target_var_in_the_middle() throws MalformedURLException {
        matchUrl("http://youtu.be?a=123&v=abcdef&x=poiw");
    }

    @Test
    public void target_var_at_the_end() throws MalformedURLException {
        matchUrl("http://youtu.be?a=123&x=poiw&v=abcdef");
    }
}
