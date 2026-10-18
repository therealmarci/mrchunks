package at.marcelr.mrchunks.util;

/**
 * Width of text in Minecraft's default font, to keep inventory titles inside the window. The client does not cut
 * titles off - a long one simply runs out of the box (a chest window has room for about 160 pixels).
 */
public final class TitleWidth {

    /** Usable title width of a chest window in pixels (176 px window, title starts at x = 8). */
    public static final int MAX = 160;

    private TitleWidth() {
    }

    /** Pixel width of one character including the 1 px gap, as in the default font. */
    static int width(int c) {
        return switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', ' ', '(', ')', '{', '}', '"', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    public static int width(String text) {
        return text.codePoints().map(TitleWidth::width).sum();
    }

    /** {@code text} if it fits into {@code max} pixels, otherwise cut and ended with "…". */
    public static String fit(String text, int max) {
        if (width(text) <= max) {
            return text;
        }
        int ellipsis = width('…');
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            if (used + width(c) + ellipsis > max) {
                break;
            }
            result.appendCodePoint(c);
            used += width(c);
            i += Character.charCount(c);
        }
        return result.toString().stripTrailing() + "…";
    }
}
