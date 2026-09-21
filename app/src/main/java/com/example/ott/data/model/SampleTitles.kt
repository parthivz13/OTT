package com.example.ott.data.model

// Fallback data when no TMDB API key is configured or the network call fails.
object SampleTitles {
    private val sampleVideos = listOf(
        "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
        "https://raw.githubusercontent.com/mediaelement/mediaelement-files/master/big_buck_bunny.mp4"
    )

    val fallback: List<Title> = listOf(
        Title(
            id = 1,
            name = "Stranger Things",
            overview = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.7,
            mediaType = "tv",
            year = "2025",
            genre = "Sci-Fi",
            badge = "🔥 TRENDING #1",
            contentRating = "U/A 16+",
            durationOrSeasons = "4 Seasons",
            audioLanguages = "English • Hindi • Tamil",
            qualityTag = "4K • Dolby Vision • 5.1"
        ),
        Title(
            id = 2,
            name = "The Mandalorian",
            overview = "After the fall of the Galactic Empire, a lone gunfighter makes his way through the outer reaches of the lawless galaxy.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.6,
            mediaType = "tv",
            year = "2024",
            genre = "Action & Sci-Fi",
            badge = "HOTSTAR SPECIAL",
            contentRating = "U/A 13+",
            durationOrSeasons = "3 Seasons",
            audioLanguages = "English • Hindi • Telugu",
            qualityTag = "4K Ultra HD • Atmos"
        ),
        Title(
            id = 3,
            name = "Extraction 2",
            overview = "Back from the brink of death, highly skilled commando Tyler Rake takes on another dangerous mission: saving the imprisoned family of a ruthless gangster.",
            posterUrl = null,
            backdropUrl = null,
            rating = 7.9,
            mediaType = "movie",
            year = "2023",
            genre = "Action Thriller",
            badge = "BLOCKBUSTER",
            contentRating = "A 18+",
            durationOrSeasons = "2h 3m",
            audioLanguages = "Hindi • English",
            qualityTag = "4K • Dolby Atmos"
        ),
        Title(
            id = 4,
            name = "Wednesday",
            overview = "Smart, sarcastic and a little dead inside, Wednesday Addams investigates a murder spree while making new friends — and foes — at Nevermore Academy.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.2,
            mediaType = "tv",
            year = "2023",
            genre = "Mystery Comedy",
            badge = "NEW SEASON",
            contentRating = "U/A 13+",
            durationOrSeasons = "2 Seasons",
            audioLanguages = "English • Hindi",
            qualityTag = "HD • 5.1 Surround"
        ),
        Title(
            id = 5,
            name = "The Boys",
            overview = "A fun and irreverent take on what happens when superheroes abuse their superpowers rather than use them for good.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.7,
            mediaType = "tv",
            year = "2024",
            genre = "Action Drama",
            badge = "POPULAR",
            contentRating = "A 18+",
            durationOrSeasons = "4 Seasons",
            audioLanguages = "English • Hindi",
            qualityTag = "4K • Dolby Vision"
        ),
        Title(
            id = 6,
            name = "House of the Dragon",
            overview = "An internal succession crisis within House Targaryen at the height of its power, 172 years before the birth of Daenerys Targaryen.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.5,
            mediaType = "tv",
            year = "2024",
            genre = "Fantasy Drama",
            badge = "NEW EPISODE",
            contentRating = "A 18+",
            durationOrSeasons = "2 Seasons",
            audioLanguages = "English • Hindi • Tamil",
            qualityTag = "4K • Dolby Atmos"
        ),
        Title(
            id = 7,
            name = "Loki",
            overview = "The mercurial villain Loki resumes his role as the God of Mischief in a new series that takes place after the events of Avengers: Endgame.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.3,
            mediaType = "tv",
            year = "2023",
            genre = "Sci-Fi Fantasy",
            badge = "MARVEL STUDIOS",
            contentRating = "U/A 13+",
            durationOrSeasons = "2 Seasons",
            audioLanguages = "Hindi • English • Tamil • Telugu",
            qualityTag = "4K • IMAX Enhanced"
        ),
        Title(
            id = 8,
            name = "Squid Game",
            overview = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games. Inside, a tempting prize awaits with deadly high stakes.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.1,
            mediaType = "tv",
            year = "2024",
            genre = "Suspense Thriller",
            badge = "TRENDING",
            contentRating = "A 18+",
            durationOrSeasons = "2 Seasons",
            audioLanguages = "Korean • Hindi • English",
            qualityTag = "4K • Dolby Vision"
        ),
        Title(
            id = 9,
            name = "The Witcher",
            overview = "Geralt of Rivia, a mutated monster-hunter for hire, journeys toward his destiny in a turbulent world where people often prove more wicked than beasts.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.0,
            mediaType = "tv",
            year = "2023",
            genre = "Action Fantasy",
            badge = "TOP 10",
            contentRating = "A 18+",
            durationOrSeasons = "3 Seasons",
            audioLanguages = "English • Hindi",
            qualityTag = "4K • Dolby Atmos"
        ),
        Title(
            id = 10,
            name = "Money Heist",
            overview = "An unusual group of robbers attempt to carry out the most perfect robbery in Spanish history - stealing 2.4 billion euros from the Royal Mint of Spain.",
            posterUrl = null,
            backdropUrl = null,
            rating = 8.2,
            mediaType = "tv",
            year = "2021",
            genre = "Crime Thriller",
            badge = "GLOBAL HIT",
            contentRating = "A 18+",
            durationOrSeasons = "5 Parts",
            audioLanguages = "Spanish • Hindi • English",
            qualityTag = "HD • 5.1 Audio"
        )
    ).mapIndexed { index, title ->
        title.copy(
            videoUrl = sampleVideos[index % sampleVideos.size],
            trailerUrl = sampleVideos[index % sampleVideos.size],
            durationSeconds = 5400 + (index * 420),
            is4K = index % 2 == 0,
            isHD = true,
            isAD = index % 3 == 0,
            seasonEpisode = "S1:E${index + 1}"
        )
    }
}
