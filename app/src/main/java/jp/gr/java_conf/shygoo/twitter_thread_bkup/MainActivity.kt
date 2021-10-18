package jp.gr.java_conf.shygoo.twitter_thread_bkup

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.GridRange
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SortRangeRequest
import com.google.api.services.sheets.v4.model.SortSpec
import com.google.api.services.sheets.v4.model.ValueRange
import jp.gr.java_conf.shygoo.twitter_thread_bkup.twitter.TwitterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {
    private val mainMessage: TextView by lazy { findViewById(R.id.main_message) }
    private val signInButton: SignInButton by lazy { findViewById(R.id.sign_in_button) }
    private val signOutButton: Button by lazy { findViewById(R.id.sign_out_button) }
    private val signedInUI: Group by lazy { findViewById(R.id.ui_for_signed_in_user) }
    private val tweetUrlForm: EditText by lazy { findViewById(R.id.tweet_url_form) }
    private val spreadsheetUrlForm: EditText by lazy { findViewById(R.id.spreadsheet_url_form) }
    private val backUpButton: Button by lazy { findViewById(R.id.backup_button) }

    // Twitter
    private val twitterRepository = TwitterRepository()

    // Google SpreadSheets
    private val credential: GoogleAccountCredential by lazy {
        GoogleAccountCredential.usingOAuth2(this, Collections.singleton(SheetsScopes.SPREADSHEETS))
    }
    private lateinit var googleSignInClient: GoogleSignInClient
    private val startSignIn = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val completedTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = try {
            completedTask.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            Log.w(TAG, "Sign in failed. ${e.statusCode}")
            null
        }
        updateUi(account)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prepareGoogleSignIn()
    }

    private fun prepareGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, options)

        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { signOut() }

        updateUi(GoogleSignIn.getLastSignedInAccount(this))
    }

    private fun updateUi(account: GoogleSignInAccount?) {
        if (account == null) {
            // Not signed yet
            signInButton.isVisible = true
            signedInUI.isVisible = false
            mainMessage.text = getString(R.string.sign_in_message)
        } else {
            // Already signed in
            signInButton.isVisible = false
            signedInUI.isVisible = true
            backUpButton.setOnClickListener { executeBackUp(account) }
            mainMessage.text = getString(R.string.already_signed_in_message, account.displayName)
        }
    }

    private fun signIn() {
        startSignIn.launch(googleSignInClient.signInIntent)
    }

    private fun signOut() {
        googleSignInClient.signOut()
        updateUi(null)
    }

    private fun executeBackUp(account: GoogleSignInAccount) {
        val tweetId = parseTweetIdFromInputUrl()
        if (tweetId == null) {
            showToast(R.string.error_message_invalid_tweet_url)
            return
        }
        Log.d(TAG, "Tweet ID: $tweetId")

        val spreadsheetId = parseSpreadsheetIdFromInputUrl()
        if (spreadsheetId == null) {
            showToast(R.string.error_message_invalid_spreadsheet_url)
            return
        }
        Log.d(TAG, "SpreadSheet ID: $spreadsheetId")

        backUpButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            // Prepare to write
            val sheetsApi = createSheetsApi(account)

            // Track replies back to the root tweet
            var rowNumber = 0
            var currentTweet = twitterRepository.getTweetById(tweetId)
            var resultMesage = R.string.error_message_tweet_not_found
            while (currentTweet != null) {
                // Write the tweet data to the SpreadSheet
                rowNumber++
                val rowData = listOf<Any>(
                    currentTweet.createdAt,
                    currentTweet.text,
                    String.format(
                        TWEET_URL_FORMAT,
                        currentTweet.user.screenName,
                        currentTweet.id
                    ),
                    rowNumber,
                )
                val isSucceeded = writeRow(sheetsApi, spreadsheetId, rowNumber, rowData)
                if (isSucceeded.not()) {
                    resultMesage = R.string.error_message_failed_to_write
                    break
                }

                // Check if the tweet is a reply (having a parent tweet)
                val parentTweetId = currentTweet.replyTo
                if (parentTweetId.isNullOrEmpty()) {
                    // Reached the root tweet
                    resultMesage = R.string.done_message
                    break
                }

                // Load the next(=parent) tweet
                delay(API_CALL_INTERVAL_MILLIS) // Avoid API call limitation
                currentTweet = twitterRepository.getTweetById(parentTweetId)
            }

            // Reverse sort
            val columnCount = 4
            val sortKeyIndex = 3
            reverseSort(sheetsApi, spreadsheetId, sortKeyIndex, columnCount, rowNumber)
            clearSortKeyColumn(sheetsApi, spreadsheetId, sortKeyIndex, rowNumber)

            // Show result
            withContext(Dispatchers.Main) {
                backUpButton.isEnabled = true
                showToast(resultMesage)
            }
        }
    }

    private fun parseTweetIdFromInputUrl(): String? {
        val maybeUrl = tweetUrlForm.text?.toString()
        Log.d(TAG, "Tweet URL: $maybeUrl")
        if (maybeUrl.isNullOrEmpty() || maybeUrl.matches(TWEET_URL_REGEX).not()) {
            return null
        }
        val uri = Uri.parse(maybeUrl)
        val pathSegments = uri.pathSegments
        val statusIndex = pathSegments.indexOfLast { it.matches(TWEET_URL_REGEX_PATH_BEFORE_ID) }
        if (statusIndex < 0 || statusIndex == pathSegments.lastIndex) {
            return null
        }
        return pathSegments[statusIndex + 1]
    }

    private fun parseSpreadsheetIdFromInputUrl(): String? {
        val maybeUrl = spreadsheetUrlForm.text?.toString()
        Log.d(TAG, "SpreadSheet URL: $maybeUrl")
        if (maybeUrl.isNullOrEmpty() || maybeUrl.startsWith(SPREAD_SHEET_URL_PREFIX_PETTERN).not()) {
            return null
        }
        val uri = Uri.parse(maybeUrl)
        val pathSegments = uri.pathSegments
        if (pathSegments.lastIndex < SPREADSHEET_ID_POSITION) {
            return null
        }
        return pathSegments[SPREADSHEET_ID_POSITION]
    }

    private fun createSheetsApi(signInAccount: GoogleSignInAccount): Sheets {
        credential.selectedAccount = signInAccount.account
        return Sheets
            .Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
            .setApplicationName(getString(R.string.app_name))
            .build()
    }

    private fun writeRow(
        sheetsApi: Sheets,
        spreadsheetId: String,
        rowNumber: Int,
        rowData: List<Any>
    ): Boolean {
        val range = "A$rowNumber:${'A' + rowData.lastIndex}$rowNumber"
        val content = ValueRange()
            .setValues(listOf(rowData))
            .setMajorDimension("ROWS")
        try {
            sheetsApi.spreadsheets()
                .values()
                .update(spreadsheetId, range, content)
                .setValueInputOption("RAW")
                .execute()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed.", e)
        }
        return false
    }

    private fun reverseSort(
        sheetsApi: Sheets,
        spreadsheetId: String,
        sortKeyIndex: Int,
        columnCount: Int,
        rowCount: Int,
    ) {
        val gridRange = GridRange()
        gridRange.sheetId = 0
        gridRange.startColumnIndex = 0
        gridRange.startRowIndex = 0
        gridRange.endColumnIndex = columnCount
        gridRange.endRowIndex = rowCount

        val sortSpec = SortSpec()
        sortSpec.sortOrder = "DESCENDING"
        sortSpec.dimensionIndex = sortKeyIndex

        val sortRangeRequest = SortRangeRequest()
        sortRangeRequest.range = gridRange
        sortRangeRequest.sortSpecs = listOf(sortSpec)

        val request = Request()
        request.sortRange = sortRangeRequest

        val batchUpdateSpreadsheetRequest = BatchUpdateSpreadsheetRequest()
        batchUpdateSpreadsheetRequest.requests = listOf(request)

        try {
            sheetsApi.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest)
                .execute()
        } catch (e: Exception) {
            Log.e(TAG, "Sort failed.", e)
        }
    }

    private fun clearSortKeyColumn(
        sheetsApi: Sheets,
        spreadsheetId: String,
        columnIndex: Int,
        rowCount: Int,
    ) {
        val column = 'A' + columnIndex
        val range = "${column}1:$column$rowCount"
        try {
            sheetsApi.spreadsheets()
                .values()
                .clear(spreadsheetId, range, ClearValuesRequest())
                .execute()
        } catch (e: Exception) {
            Log.e(TAG, "Clear column $column failed.", e)
        }
    }

    private fun showToast(@StringRes messageId: Int) = showToast(getString(messageId))

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "MainActivity"

        private const val TWEET_URL_FORMAT = "https://twitter.com/%s/status/%s"
        private val TWEET_URL_REGEX =
            Regex("^https?://(mobile\\.|www\\.)?twitter.com/.*/status(es)?/.*")
        private val TWEET_URL_REGEX_PATH_BEFORE_ID = Regex("^status(es)?$")

        private const val SPREAD_SHEET_URL_PREFIX_PETTERN =
            "https://docs.google.com/spreadsheets/d/"
        private const val SPREADSHEET_ID_POSITION = 2

        private const val API_CALL_INTERVAL_MILLIS = 1_000L
    }
}
