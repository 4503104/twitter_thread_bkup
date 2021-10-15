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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val mainMessage: TextView by lazy { findViewById(R.id.main_message) }
    private val signInButton: SignInButton by lazy { findViewById(R.id.sign_in_button) }
    private val signOutButton: Button by lazy { findViewById(R.id.sign_out_button) }
    private val urlFormGroup: Group by lazy { findViewById(R.id.url_form_group) }
    private val sheetUrlForm: EditText by lazy { findViewById(R.id.sheet_url_form) }
    private val backUpButton: Button by lazy { findViewById(R.id.backup_button) }

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

        backUpButton.setOnClickListener { executeBackUp() }
        prepareGoogleSignIn()
    }

    private fun prepareGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(SHEETS_API_ENDPOINT))
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
            signOutButton.isVisible = false
            urlFormGroup.isVisible = false
            mainMessage.text = getString(R.string.sign_in_message)
        } else {
            // Already signed in
            signInButton.isVisible = false
            signOutButton.isVisible = true
            urlFormGroup.isVisible = true
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

    private fun executeBackUp() {
        val maybeSheetId = parseSheetIdFromInputUrl()
        if (maybeSheetId == null) {
            showToast(R.string.error_message_invalid_url)
            return
        }
        showToast("id: $maybeSheetId")
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

    private fun showToast(@StringRes messageId: Int) = showToast(getString(messageId))

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "MainActivity"
        /** Read & Write endpoint. https://developers.google.com/sheets/api/guides/authorizing */
        private const val SHEETS_API_ENDPOINT = "https://www.googleapis.com/auth/spreadsheets"

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
