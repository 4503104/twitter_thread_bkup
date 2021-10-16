package jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TwitterApi {

    @GET("tweets")
    suspend fun getTweetsByIds(
        @Header("Authorization") accessToken: String,
        @Query("ids") ids: String
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://api.twitter.com/2/"
    }
}
