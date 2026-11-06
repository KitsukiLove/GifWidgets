package app.gifwidgets

import app.gifwidgets.ui.design.ScreenWindow
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gifwidgets.ui.theme.AppTheme

/** Privacy screen: what the app really asks the system for (read from the installed package) and what it uses. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(title = stringResource(R.string.win_privacy), onClose = { finish() }) { PrivacyScreen() }
                }
            }
        }
    }
}

/** Permissions this app declares in its own merged manifest. */
private fun requestedPermissions(context: Context): List<String> {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    }
    return info.requestedPermissions?.toList().orEmpty()
}

@Composable
private fun PrivacyScreen() {
    val context = LocalContext.current
    val permissions = remember { try { requestedPermissions(context) } catch (e: PackageManager.NameNotFoundException) { emptyList() } }
    val hasInternet = Manifest.permission.INTERNET in permissions
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.privacy_permissions), style = MaterialTheme.typography.titleMedium)
            if (permissions.isEmpty()) {
                Text(stringResource(R.string.privacy_permissions_none))
            } else {
                Text(stringResource(R.string.privacy_permissions_some, permissions.size))
                permissions.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(if (hasInternet) R.string.privacy_internet_yes else R.string.privacy_internet_no))

            Text(stringResource(R.string.privacy_data_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_data_body))

            Text(stringResource(R.string.privacy_backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_backup_body))

            Text(stringResource(R.string.privacy_background_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_background_body))

            Text(stringResource(R.string.privacy_deps_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_deps_body))
        }
    }
}
