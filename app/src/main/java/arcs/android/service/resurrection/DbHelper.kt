package arcs.android.service.resurrection

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Parcel
import android.os.PersistableBundle
import androidx.core.database.sqlite.transaction

internal class DbHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    /* name = */ "resurrection_data",
    /* cursorFactory = */ null,
    /* version = */ 1
) {
    override fun onCreate(db: SQLiteDatabase?) {
        try {
            db?.beginTransaction()
            CREATE_TABLE.forEach { db?.execSQL(it) }
            db?.setTransactionSuccessful()
        } finally {
            db?.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) = Unit

    internal fun registerRequest(resurrectionRequest: ResurrectionRequest) {
        writableDatabase.use { db ->
            db.transaction {
                val requestContent = ContentValues()
                    .apply {
                        put("component_package", resurrectionRequest.componentName.packageName)
                        put("component_class", resurrectionRequest.componentName.className)
                        put("component_type", resurrectionRequest.componentType.name)
                        put("intent_action", resurrectionRequest.intentAction)
                        val extrasBlob = if (resurrectionRequest.intentExtras != null) {
                            with(Parcel.obtain()) {
                                writeTypedObject(resurrectionRequest.intentExtras, 0)
                                marshall()
                            }
                        } else null
                        put("intent_extras", extrasBlob)
                    }
                db.insertWithOnConflict(
                    "resurrection_requests",
                    null,
                    requestContent,
                    SQLiteDatabase.CONFLICT_REPLACE
                )

                db.delete(
                    "requested_notifiers",
                    "component_package = ? AND component_class = ?",
                    arrayOf(
                        resurrectionRequest.componentName.packageName,
                        resurrectionRequest.componentName.className
                    )
                )

                val notifierValues = ContentValues()
                resurrectionRequest.notifyOn.forEach {
                    notifierValues.put(
                        "component_package",
                        resurrectionRequest.componentName.packageName
                    )
                    notifierValues.put(
                        "component_class",
                        resurrectionRequest.componentName.className
                    )
                    notifierValues.put("notification_key", it)
                    db.insert("requested_notifiers", null, notifierValues)
                }
            }
        }
    }

    internal fun getRegistrations(): List<ResurrectionRequest> {
        val notifiersByComponentName = mutableMapOf<ComponentName, MutableList<String>>()
        val result = mutableListOf<ResurrectionRequest>()

        readableDatabase.use { db ->
            db.transaction {
                db.rawQuery(
                    "SELECT component_package, component_class, notification_key FROM requested_notifiers",
                    null
                ).use {
                    while (it.moveToNext()) {
                        val componentName = ComponentName(it.getString(0), it.getString(1))
                        val key = it.getString(2)

                        val notifiers = notifiersByComponentName[componentName] ?: mutableListOf()
                        notifiers.add(key)
                        notifiersByComponentName[componentName] = notifiers
                    }
                }

                db.rawQuery(
                    """
                        SELECT 
                            component_package, 
                            component_class, 
                            component_type, 
                            intent_action, 
                            intent_extras 
                        FROM resurrection_requests
                    """.trimIndent(),
                    null
                ).use {
                    while (it.moveToNext()) {
                        val componentName = ComponentName(it.getString(0), it.getString(1))
                        val type = ResurrectionRequest.ComponentType.valueOf(it.getString(2))
                        val action = if (it.isNull(3)) null else it.getString(3)
                        val extras = if (it.isNull(4)) null else {
                            with(Parcel.obtain()) {
                                val bytes = it.getBlob(4)
                                unmarshall(bytes, 0, bytes.size)
                                setDataPosition(0)
                                readTypedObject(PersistableBundle.CREATOR)
                            }
                        }

                        result.add(
                            ResurrectionRequest(
                                componentName,
                                type,
                                action,
                                extras,
                                notifiersByComponentName[componentName] ?: emptyList()
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    companion object {
        private val CREATE_TABLE = arrayOf(
            """
                CREATE TABLE resurrection_requests (
                    component_package TEXT NOT NULL,
                    component_class TEXT NOT NULL,
                    component_type TEXT NOT NULL,
                    intent_action TEXT,
                    intent_extras BLOB,
                    PRIMARY KEY (component_package, component_class)
                )
            """.trimIndent(),
            """
                CREATE TABLE requested_notifiers (
                    component_package TEXT NOT NULL,
                    component_class TEXT NOT NULL,
                    notification_key TEXT NOT NULL
                )
            """.trimIndent(),
            """
                CREATE INDEX notifiers_by_component 
                ON requested_notifiers (
                    component_package, 
                    component_class
                )
            """.trimIndent()
        )
    }
}
