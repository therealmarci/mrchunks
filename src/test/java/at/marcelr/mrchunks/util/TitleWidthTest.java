package at.marcelr.mrchunks.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleWidthTest {

    @Test
    void shortTitleStaysUnchanged() {
        assertEquals("MRchunks » Übersicht", TitleWidth.fit("MRchunks » Übersicht", TitleWidth.MAX));
    }

    @Test
    void longTitleIsCutWithEllipsis() {
        String fitted = TitleWidth.fit("Mitglieder » alle Chunkloader von _therealmarci", TitleWidth.MAX);
        assertTrue(fitted.endsWith("…"), fitted);
        assertTrue(TitleWidth.width(fitted) <= TitleWidth.MAX, fitted);
    }

    @Test
    void narrowCharactersCountLess() {
        assertTrue(TitleWidth.width("iiii") < TitleWidth.width("MMMM"));
    }
}
