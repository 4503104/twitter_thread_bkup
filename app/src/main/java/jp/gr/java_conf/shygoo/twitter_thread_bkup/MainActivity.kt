package jp.gr.java_conf.shygoo.twitter_thread_bkup

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
            mainMessage.text = getString(R.string.sign_in_message)
        } else {
            // Already signed in
            signInButton.isVisible = false
            signOutButton.isVisible = true
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

    companion object {
        private const val TAG = "MainActivity"
        /** Read & Write endpoint. https://developers.google.com/sheets/api/guides/authorizing */
        private const val SHEETS_API_ENDPOINT = "https://www.googleapis.com/auth/spreadsheets"
    }
}
