package com.invictus.xmd.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowDetectorTest {

    @Test fun seasonEpisode() {
        assertTrue(ShowDetector.isEpisode("The.Boys.S03E05.1080p.WEB-DL.x264.mkv"))
        assertEquals("The Boys", ShowDetector.showFolderName("The.Boys.S03E05.1080p.WEB-DL.x264.mkv"))
    }

    @Test fun crossAndWordEpisode() {
        assertEquals("Breaking Bad", ShowDetector.showFolderName("Breaking Bad 1x02.mp4"))
        assertEquals("Show Name", ShowDetector.showFolderName("Show Name Episode 12.mp4"))
        assertEquals("Naruto", ShowDetector.showFolderName("Naruto Ep.045 English Sub.mp4"))
    }

    @Test fun animeStyle() {
        assertTrue(ShowDetector.isEpisode("[SubsPlease] Frieren - 05 (1080p) [ABC].mkv"))
        assertEquals("Frieren", ShowDetector.showFolderName("[SubsPlease] Frieren - 05 (1080p) [ABC].mkv"))
    }

    @Test fun notEpisodes() {
        assertFalse(ShowDetector.isEpisode("Movie.2019.1080p.BluRay.x264.mkv"))
        assertFalse(ShowDetector.isEpisode("holiday 1920x1080.mp4"))
        assertFalse(ShowDetector.isEpisode("Movie - 1080.mkv"))
        assertFalse(ShowDetector.isEpisode("report ep 5.pdf"))
    }

    @Test fun audioChannelsPlusCodecIsNotAnEpisode() {
        // "DDP5.1x264" is 5.1 audio + the x264 codec, not season 1 episode 264.
        assertFalse(ShowDetector.isEpisode("Hope.2026.1080p.AMZN.WEB-DL.Hindi.LiNE.DD2.0-Korean.DDP5.1x264-1VeGAS.mkv"))
        assertFalse(ShowDetector.isEpisode("Movie.2024.1080p.WEB-DL.DDP5.1x265-GRP.mkv"))
        assertFalse(ShowDetector.isEpisode("Film.2023.720p.BluRay.DD7.1x264-X.mkv"))
        assertFalse(ShowDetector.isEpisode("Film.2023.AAC2.0x264.mkv"))
        assertFalse(ShowDetector.isEpisodeOrSeasonPack("Hope.2026.1080p.AMZN.WEB-DL.Hindi.LiNE.DD2.0-Korean.DDP5.1x264-1VeGAS"))
        assertFalse(ShowDetector.titleLooksLikeEpisode("Movie.2024.1080p.WEB-DL.DDP5.1x265-GRP"))
    }

    @Test fun realCrossEpisodesStillDetected() {
        assertTrue(ShowDetector.isEpisode("Show Name 01x12.mkv"))
        assertTrue(ShowDetector.isEpisode("Show.Name.10x105.HDTV.mkv"))
        assertEquals("Lost", ShowDetector.showFolderName("Lost 6x17 The End.mkv"))
    }

    @Test fun fallbackAndUnsorted() {
        assertEquals("Unsorted", ShowDetector.showFolderName("Ep 5.mp4"))
        assertEquals("Some Show", ShowDetector.showFolderName("Ep 5.mp4", titleHint = "Some Show Episode 5"))
        assertEquals("Some Show", ShowDetector.showFolderName("Ep 5.mp4", sourceUrl = "https://cdn.example.com/Some%20Show/Ep%205.mp4"))
    }

    @Test fun seasonPackTorrent() {
        assertTrue(ShowDetector.isEpisodeOrSeasonPack("Show.Name.S01.1080p.BluRay"))
        assertEquals("Show Name", ShowDetector.showFolderName("Show.Name.S01.1080p.BluRay"))
        assertFalse(ShowDetector.isEpisodeOrSeasonPack("Some.Movie.2020.1080p"))
    }
}
