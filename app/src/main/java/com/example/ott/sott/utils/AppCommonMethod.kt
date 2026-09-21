package com.example.ott.sott.utils

import com.example.ott.types.Asset
import com.example.ott.types.AssetImage

object AppCommonMethod {
    fun addSeasonAndEpisodeNo(asset: Asset): String? =
        asset.tags?.get("SeasonEpisode")?.toString()

    fun getMetaByTag(asset: Asset, tag: String): String? =
        asset.metas?.get(tag)?.toString()

    fun getMetas(asset: Asset): String =
        asset.metas?.get("Metadata")?.toString().orEmpty()

    fun getQualities(asset: Asset): List<String> {
        val list = mutableListOf<String>()
        val tags = asset.tags
        if (tags != null) {
            if (tags.containsKey("4K")) list.add("4K")
            if (tags.containsKey("HD")) list.add("HD")
            if (tags.containsKey("AD")) list.add("AD")
        }
        return list
    }

    fun getTagsFromAsset(asset: Asset, tag: String): String =
        asset.tags?.get(tag)?.toString().orEmpty()

    fun getCardwiseImage(
        images: List<AssetImage>,
        ratio: String,
        width: Int,
        height: Int
    ): String? = images.firstOrNull { it.ratio == ratio }?.url ?: images.firstOrNull()?.url
}
