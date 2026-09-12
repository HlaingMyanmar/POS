package com.sspd.servicemgmt.feature.compose

data class ListRow(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val trailing: String = "",
)

data class MasterUiState(
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<ListRow> = emptyList(),
    val query: String = "",
    val canCreate: Boolean = false,
    val editorTitle: String? = null,
    val fieldA: String = "",
    val fieldB: String = "",
    val fieldC: String = "",
    val toggle: Boolean = true,
    val infoBody: String? = null,
    val metrics: List<Pair<String, String>> = emptyList(),
)
