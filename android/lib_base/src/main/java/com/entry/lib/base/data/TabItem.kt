package com.entry.lib.base.data

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.android.parcel.Parcelize

@Parcelize
data class TabItem(
    val id: Int,
    val title: String,
    @DrawableRes val iconRes: Int
) : Parcelable