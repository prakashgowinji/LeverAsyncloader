package com.pinload.datamodel

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("username") val userName: String,
    @SerializedName("name") val name: String,
    @SerializedName("profile_image") val profileImage: ProfileImage,
    @SerializedName("links") val links: Link
)


data class ProfileImage(
    @SerializedName("small") val small: String,
    @SerializedName("medium") val medium: String,
    @SerializedName("large") val large: String
)

data class Link(
    @SerializedName("self") val self: String?,
    @SerializedName("html") val html: String?,
    @SerializedName("photos") val photos: String?,
    @SerializedName("likes") val likes: String?,
    @SerializedName("download") val download: String?
)

data class Url(
    @SerializedName("raw") val raw: String,
    @SerializedName("full") val full: String,
    @SerializedName("regular") val regular: String,
    @SerializedName("small") val small: String,
    @SerializedName("thumb") val thumb: String
)

data class Category(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("photo_count") val photoCount: Int,
    @SerializedName("links") val links: Link
)

data class ItemInfo(
    @SerializedName("id") val id: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("color") val color:String,
    @SerializedName("likes") val likes: Int,
    @SerializedName("liked_by_user") val likedByUser: Boolean,
    @SerializedName("user") val user: User,
    @SerializedName("urls") val urls: Url,
    @SerializedName("categories") val categories: List<Category>,
    @SerializedName("links") val links: Link
)