package arcs.android.service.resurrection

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Base [Service] implementation for services which would like to support resurrecting their
 * clients.
 */
abstract class ResurrectorService : Service() {
    private val dbHelper: DbHelper by lazy { DbHelper(this) }
    private var registeredRequests = setOf<ResurrectionRequest>()
    private var registeredRequestsByNotifiers = mapOf<String, Set<ResurrectionRequest>>()
    private val job = Job() + Dispatchers.IO + CoroutineName("ResurrectorService")
    private val mutex = Mutex()

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(job).loadRequests()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ResurrectionRequest.createFromIntent(intent)?.let { CoroutineScope(job).registerRequest(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancelChildren()
    }

    private fun CoroutineScope.loadRequests() = launch {
        val byNotifiers = mutableMapOf<String, MutableSet<ResurrectionRequest>>()

        val registrations = async { dbHelper.getRegistrations() }.await()

        mutex.withLock {
            registeredRequests = registrations.toSet().onEach { req ->
                req.notifyOn.forEach {
                    val list = byNotifiers[it] ?: mutableSetOf()
                    list.add(req)
                    byNotifiers[it] = list
                }
                if (req.notifyOn.isEmpty()) {
                    val list = byNotifiers[""] ?: mutableSetOf()
                    list.add(req)
                    byNotifiers[""] = list
                }
            }
            registeredRequestsByNotifiers = byNotifiers
        }
    }

    private fun CoroutineScope.registerRequest(request: ResurrectionRequest) = launch {
        dbHelper.registerRequest(request)

        mutex.withLock {
            registeredRequests = registeredRequests + request
            registeredRequestsByNotifiers = registeredRequestsByNotifiers.toMutableMap().apply {
                request.notifyOn.forEach {
                    val list = this[it]?.toMutableSet() ?: mutableSetOf()
                    list.add(request)
                    this[it] = list
                }
                if (request.notifyOn.isEmpty()) {
                    val list = this[""]?.toMutableSet() ?: mutableSetOf()
                    list.add(request)
                    this[""] = list
                }
            }
        }
    }

    /**
     * Makes [Context.startService] or [Context.startActivity] calls to all clients who are
     * registered for the specified [events] (or are registered for *all* events).
     */
    protected fun resurrectClients(vararg events: String) = resurrectClients(events.toList())

    /**
     * Makes [Context.startService] or [Context.startActivity] calls to all clients who are
     * registered for the specified [events] (or are registered for *all* events).
     */
    protected fun resurrectClients(events: List<String>) {
        CoroutineScope(job).launch {
            val requests = mutableSetOf<ResurrectionRequest>()
            mutex.withLock {
                events.forEach { event ->
                    registeredRequestsByNotifiers[event]?.let { requests.addAll(it) }
                }
                registeredRequestsByNotifiers[""]?.let { requests.addAll(it) }
            }

            withContext(Dispatchers.Main) {
                requests.forEach { it.issueResurrection(events) }
            }
        }
    }

    private fun ResurrectionRequest.issueResurrection(events: List<String>) {
        val intent = Intent()
        intent.component = this.componentName
        intent.action = this.intentAction
        this.intentExtras?.let { intent.putExtras(it) }
        intent.putStringArrayListExtra(
            ResurrectionRequest.EXTRA_RESURRECT_NOTIFIER,
            ArrayList(events)
        )

        when (this.componentType) {
            ResurrectionRequest.ComponentType.Activity -> startActivity(intent)
            ResurrectionRequest.ComponentType.Service -> startService(intent)
        }
    }
}
