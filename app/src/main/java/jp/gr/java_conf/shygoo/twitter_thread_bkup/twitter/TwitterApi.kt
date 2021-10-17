package jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TwitterApi {

    @GET("/1.1/statuses/show.json")
    suspend fun getTweetById(
        @Header("Authorization") accessToken: String,
        @Query("id") id: String = "",
        @Query("tweet_mode") tweetMode: String? = null,
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://api.twitter.com"
    }
}
