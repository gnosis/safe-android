package pm.gnosis.heimdall.common.utils

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.widget.Toolbar
import android.view.View

inline fun SharedPreferences.edit(func: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.func()
    editor.apply()
}

inline fun FragmentManager.transaction(func: FragmentTransaction.() -> FragmentTransaction) {
    beginTransaction().func().commit()
}

fun Activity.setupToolbar(toolbar: Toolbar, @DrawableRes icon: Int) {
    toolbar.setup({ finish() }, icon)
}

inline fun Toolbar.setup(crossinline func: (Toolbar) -> Unit, @DrawableRes icon: Int) {
    setNavigationIcon(icon)
    setNavigationOnClickListener { func(this) }
}

fun Boolean.toVisibility(hiddenVisibility: Int = View.GONE) =
        if (this) View.VISIBLE else hiddenVisibility

fun View.visible(visible: Boolean, hiddenVisibility: Int = View.GONE) {
    this.visibility = if (visible) View.VISIBLE else hiddenVisibility
}

inline fun Bundle.build(func: Bundle.() -> Unit): Bundle {
    this.func()
    return this
}


fun Fragment.withArgs(args: Bundle): Fragment {
    this.arguments = args
    return this
}
