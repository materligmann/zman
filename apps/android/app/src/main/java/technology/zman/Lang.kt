package technology.zman

import android.content.Context
import technology.zman.core.Lang

/** Langue de l'interface, telle que résolue par les ressources (values, values-en, values-iw). */
fun Context.lang(): Lang = Lang.of(getString(R.string.lang))
