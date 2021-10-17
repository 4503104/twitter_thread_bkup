package jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter.model

data class Tweet(
    val idStr: String,
    val fullText: String,
    val createdAt: String,
    val user: User,
    val inReplyToStatusIdStr: String?,
)
