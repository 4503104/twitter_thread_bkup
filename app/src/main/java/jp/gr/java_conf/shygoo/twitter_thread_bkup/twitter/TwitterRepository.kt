package jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter

import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import jp.gr.java_conf.shygoo.twitter_thread_bkup.BuildConfig
import jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter.model.Tweet
import jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter.model.TweetsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.lang.Exception

class TwitterRepository {
    private val api: TwitterApi = Retrofit
        .Builder()
        .baseUrl(TwitterApi.BASE_URL)
        .build()
        .create(TwitterApi::class.java)
    private val accessToken = "Bearer ${BuildConfig.TWITTER_BEARER_TOKEN}"
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    suspend fun getTweetById(tweetId: String): Tweet? = withContext(Dispatchers.IO) {
        try {
            val response = api.getTweetsByIds(
                accessToken = accessToken,
                ids = tweetId,
                tweetFields = "created_at,author_id,conversation_id"
            )
            return@withContext parse(response)?.data?.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Get tweet failed.", e)
            return@withContext null
        }
    }

    private fun parse(response: ResponseBody): TweetsResponse? {
        val json = response.string()
        Log.d(TAG, "raw JSON: $json")

        return gson.fromJson(json, TweetsResponse::class.java)
    }

    companion object {
        private const val TAG = "TwitterRepository"
    }
}
