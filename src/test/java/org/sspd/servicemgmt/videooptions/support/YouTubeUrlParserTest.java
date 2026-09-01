package org.sspd.servicemgmt.videooptions.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YouTubeUrlParserTest {

    @Test
    void parsesCommonYoutubeUrlShapes() {
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("https://youtu.be/abcDEFghijk"));
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("https://www.youtube.com/watch?v=abcDEFghijk"));
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("https://youtube.com/embed/abcDEFghijk"));
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("https://www.youtube.com/shorts/abcDEFghijk"));
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("https://www.youtube.com/live/abcDEFghijk"));
        assertEquals("abcDEFghijk", YouTubeUrlParser.parseVideoId("abcDEFghijk"));
    }

    @Test
    void rejectsBlankOrUnknownUrls() {
        assertThrows(IllegalArgumentException.class, () -> YouTubeUrlParser.parseVideoId(""));
        assertThrows(IllegalArgumentException.class, () -> YouTubeUrlParser.parseVideoId("https://example.com/watch?v=abcDEFghijk"));
    }

    @Test
    void buildsWatchAndThumbnailUrls() {
        assertEquals("https://youtu.be/abcDEFghijk", YouTubeUrlParser.watchUrl("abcDEFghijk"));
        assertEquals("https://img.youtube.com/vi/abcDEFghijk/hqdefault.jpg", YouTubeUrlParser.thumbnailUrl("abcDEFghijk"));
    }
}
