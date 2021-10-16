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
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val mainMessage: TextView by lazy { findViewById(R.id.main_message) }
    private val signInButton: SignInButton by lazy { findViewById(R.id.sign_in_button) }
    private val signOutButton: Button by lazy { findViewById(R.id.sign_out_button) }
    private val signedInUI: Group by lazy { findViewById(R.id.ui_for_signed_in_user) }
    private val tweetUrlForm: EditText by lazy { findViewById(R.id.tweet_url_form) }
    private val sheetUrlForm: EditText by lazy { findViewById(R.id.sheet_url_form) }
    private val backUpButton: Button by lazy { findViewById(R.id.backup_button) }
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

        val sheetId = parseSheetIdFromInputUrl()
        if (sheetId == null) {
            showToast(R.string.error_message_invalid_sheet_url)
            return
        }
        Log.d(TAG, "Sheet ID: $sheetId")

        backUpButton.isEnabled = false

        lifecycleScope.launch {
            val isSucceeded = writeSomethingTo(account, sheetId)
            withContext(Dispatchers.Main) {
                showToast(if (isSucceeded) R.string.done_message else R.string.error_message_failed)
                backUpButton.isEnabled = true
            }
        }
    }

    private fun parseTweetIdFromInputUrl(): String? {
        val maybeUrl = tweetUrlForm.text?.toString()
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

    private fun parseSheetIdFromInputUrl(): String? {
        val maybeUrl = sheetUrlForm.text?.toString()
        if (maybeUrl.isNullOrEmpty() || maybeUrl.startsWith(SHEET_URL_PREFIX_PETTERN).not()) {
            return null
        }
        val uri = Uri.parse(maybeUrl)
        val pathSegments = uri.pathSegments
        if (pathSegments.size < SHEET_URL_MINIMUM_PATH_COUNT) {
            return null
        }
        return pathSegments[SHEET_URL_SHEET_ID_POSITION]
    }

    private suspend fun writeSomethingTo(
        signInAccount: GoogleSignInAccount,
        sheetId: String
    ): Boolean = withContext(Dispatchers.IO) {
        credential.selectedAccount = signInAccount.account
        val sheetsApi = Sheets
            .Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        val range = "A1:B100"

        val dummyValues: MutableList<List<Any>> = mutableListOf()
        for (i in 1..100) {
            dummyValues.add(listOf("$i.", UUID.randomUUID().toString()))
        }
        val contents = ValueRange()
            .setValues(dummyValues)
            .setMajorDimension("ROWS")

        try {
            sheetsApi.spreadsheets()
                .values()
                .update(sheetId, range, contents)
                .setValueInputOption("RAW")
                .execute()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed.", e)
        }
        return@withContext false
    }

    private fun showToast(@StringRes messageId: Int) = showToast(getString(messageId))

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "TwitterThreadBackup"

        private val TWEET_URL_REGEX = Regex("^https?://(mobile\\.|www\\.)?twitter.com/.*/status(es)?/.*")
        private val TWEET_URL_REGEX_PATH_BEFORE_ID = Regex("^status(es)?$")

        private const val SHEET_URL_SCHEME = "https"
        private const val SHEET_URL_HOST = "docs.google.com"
        private const val SHEET_URL_PATH1 = "spreadsheets"
        private const val SHEET_URL_PATH2 = "d"
        private const val SHEET_URL_PREFIX_PETTERN =
            "$SHEET_URL_SCHEME://$SHEET_URL_HOST/$SHEET_URL_PATH1/$SHEET_URL_PATH2/"
        private const val SHEET_URL_MINIMUM_PATH_COUNT = 3
        private const val SHEET_URL_SHEET_ID_POSITION = 2
    }
}
