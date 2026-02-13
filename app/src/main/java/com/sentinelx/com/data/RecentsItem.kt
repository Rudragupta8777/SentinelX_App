package com.sentinelx.com.data

sealed class RecentsItem {
    data class Header(val title: String) : RecentsItem()
    data class Log(val data: CallLogItem) : RecentsItem()
}