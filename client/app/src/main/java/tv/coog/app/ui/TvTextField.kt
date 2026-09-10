package tv.coog.app.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.tv.material3.MaterialTheme

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    uri: Boolean = false,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val onChange = rememberUpdatedState(onValueChange)
    val current = rememberUpdatedState(value)
    var chromeFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (chromeFocused) 0.16f else 0.08f))
            .then(
                if (chromeFocused) Modifier.border(2.dp, Color.White, shape) else Modifier,
            )
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .heightIn(min = 52.dp)
            .onFocusChanged { chromeFocused = it.hasFocus },
        factory = { context ->
            EditText(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setTextColor(onSurface)
                setHintTextColor(muted)
                textSize = 16f
                isSingleLine = true
                isFocusable = true
                isFocusableInTouchMode = true
                showSoftInputOnFocus = true
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                inputType = if (uri) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                }
                gravity = Gravity.CENTER_VERTICAL
                hint = placeholder
                setText(current.value)
                setSelection(current.value.length)
                setOnFocusChangeListener { v, hasFocus ->
                    chromeFocused = hasFocus
                    if (hasFocus) v.showIme(forced = false)
                }
                setOnClickListener { v ->
                    v.requestFocus()
                    v.showIme(forced = true)
                }
                doAfterTextChanged { text ->
                    onChange.value(text?.toString().orEmpty())
                }
            }
        },
        update = { view ->
            val next = current.value
            if (view.text.toString() != next) {
                val sel = view.selectionEnd.coerceIn(0, next.length)
                view.setText(next)
                view.setSelection(sel)
            }
            if (view.hint?.toString().orEmpty() != placeholder) {
                view.hint = placeholder
            }
        },
    )
}

private fun View.showIme(forced: Boolean) {
    requestFocus()
    post {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return@post
        val flags = if (forced) InputMethodManager.SHOW_FORCED else InputMethodManager.SHOW_IMPLICIT
        if (!imm.showSoftInput(this, flags) && forced) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
    }
}
