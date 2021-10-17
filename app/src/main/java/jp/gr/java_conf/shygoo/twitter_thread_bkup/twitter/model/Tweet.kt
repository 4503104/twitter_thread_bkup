package jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter.model

import com.google.gson.annotations.SerializedName

data class Tweet(
    @SerializedName("id_str")
    val id: String,
    @SerializedName("full_text")
    val text: String,
    val createdAt: String,
    val user: User,
    @SerializedName("in_reply_to_status_id_str")
    val replyTo: String?,
)
