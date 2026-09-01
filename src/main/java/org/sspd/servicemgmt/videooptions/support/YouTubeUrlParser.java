package org.sspd.servicemgmt.videooptions.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.|m\\.)?(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})"
    );
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private YouTubeUrlParser() {
    }

    public static String parseVideoId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("YouTube URL ဖြည့်ပါ");
        }
        String value = raw.trim();
        if (BARE_ID.matcher(value).matches()) {
            return value;
        }
        Matcher matcher = VIDEO_ID.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("YouTube URL မမှန်ပါ");
    }

    public static String watchUrl(String videoId) {
        return "https://youtu.be/" + videoId;
    }

    public static String thumbnailUrl(String videoId) {
        return "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
