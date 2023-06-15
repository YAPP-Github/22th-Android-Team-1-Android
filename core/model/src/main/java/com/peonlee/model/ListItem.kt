package com.peonlee.model

interface ListItem {
    val id: Long
    val viewType: Enum<*>
}

/**
 * 메인-홈
 */
interface MainHomeListItem : ListItem
enum class MainHomeViewType {
    TITLE // 제목
}
