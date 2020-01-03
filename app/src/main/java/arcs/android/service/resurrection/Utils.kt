package arcs.android.service.resurrection

import android.content.Intent
import android.os.PersistableBundle

@Suppress("UNCHECKED_CAST")
internal fun Intent.putExtras(persistableBundle: PersistableBundle) {
    persistableBundle.keySet().forEach { key ->
        when (val extra = persistableBundle[key]) {
            is Boolean -> putExtra(key, extra)
            is Double -> putExtra(key, extra)
            is DoubleArray -> putExtra(key, extra)
            is Int -> putExtra(key, extra)
            is IntArray -> putExtra(key, extra)
            is Long -> putExtra(key, extra)
            is LongArray -> putExtra(key, extra)
            is String -> putExtra(key, extra)
            is Array<*> -> (extra as? Array<String>)?.let { putExtra(key, it) }
            is PersistableBundle -> putExtra(key, extra)
        }
    }
}
