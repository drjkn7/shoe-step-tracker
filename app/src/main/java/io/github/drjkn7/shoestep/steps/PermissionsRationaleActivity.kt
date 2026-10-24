package io.github.drjkn7.shoestep.steps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ShoeStepApp
import io.github.drjkn7.shoestep.ui.common.FilterTouchesWhenObscured
import io.github.drjkn7.shoestep.ui.common.NavIcon
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.ScreenColumn
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/**
 * Privacy explanation that Health Connect shows when the user asks why this app wants steps.
 * Exported (Health Connect and the system open it), so it shows static text only: it reads no
 * intent extras, no data, and offers no way into the rest of the app (SEC-03).
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The theme setting is the only thing read: no intent extras, no user data.
        val settings = (application as ShoeStepApp).container.settings
        setContent {
            ShoeStepTheme(settings) {
                RationaleScreen(onClose = ::finish)
            }
        }
    }
}

/** PNG 22: the explanation on one card, then Close. The text stays steps-only until distance is read (Phase 4). */
@Composable
fun RationaleScreen(onClose: () -> Unit) {
    FilterTouchesWhenObscured() // SEC-11
    SilverScreen(title = stringResource(R.string.rationale_title), navIcon = NavIcon.CLOSE, onNavigate = onClose) {
        ScreenColumn {
            SilverCard {
                Column(verticalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                    listOf(
                        R.string.rationale_what,
                        R.string.rationale_why,
                        R.string.rationale_stays,
                        R.string.rationale_control,
                    ).forEach {
                        Text(
                            stringResource(it),
                            style = SilverTheme.type.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                            color = SilverTheme.colors.textPrimary,
                        )
                    }
                }
            }
            PrimaryButton(stringResource(R.string.rationale_close), onClose, Modifier.fillMaxWidth())
        }
    }
}
