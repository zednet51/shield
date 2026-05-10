package com.aggregatorx.app.ui.viewmodel

/** Holds the result of a full video-preview extraction (URL + playback headers). */
data class VideoPreviewResult(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: String? = null,
    val format: String? = null,
    val isStream: Boolean = false
)
