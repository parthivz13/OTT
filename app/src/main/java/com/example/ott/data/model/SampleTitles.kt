package com.example.ott.data.model

/**
 * Offline fallback used when no TMDB API key is configured (or the network call fails), so the
 * stack carousel has something to render for local testing without hitting the network.
 */
object SampleTitles {
    val fallback: List<Title> = listOf(
        Title(1, "Stranger Things", "", null, null, 8.6, "tv", "2025", "Sci-Fi", "Newly Added"),
        Title(2, "The Mandalorian", "", null, null, 8.5, "tv", "2023", "Sci-Fi"),
        Title(3, "Extraction 2", "", null, null, 7.1, "movie", "2023", "Action", "Newly Added"),
        Title(4, "Wednesday", "", null, null, 8.1, "tv", "2022", "Comedy"),
        Title(5, "The Boys", "", null, null, 8.4, "tv", "2024", "Action"),
        Title(6, "House of the Dragon", "", null, null, 8.4, "tv", "2024", "Fantasy", "New Episode"),
        Title(7, "Money Heist", "", null, null, 8.2, "tv", "2021", "Thriller"),
        Title(8, "Squid Game", "", null, null, 8.0, "tv", "2024", "Thriller"),
        Title(9, "The Witcher", "", null, null, 8.0, "tv", "2023", "Fantasy"),
        Title(10, "Loki", "", null, null, 8.2, "tv", "2023", "Sci-Fi")
    )
}
