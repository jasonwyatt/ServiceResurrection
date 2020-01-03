package us.jwf.serviceresurrection

import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import arcs.android.service.resurrection.ResurrectorService

class ServerService : ResurrectorService() {
    private lateinit var clients: Clients

    override fun onCreate() {
        super.onCreate()
        clients = Clients()
    }

    override fun onBind(intent: Intent): IBinder? = ServerServiceImpl(clients)

    inner class Clients {
        private var nextListenerId: Int = 1
        private val listeners = mutableMapOf<Int, IListener>()

        @Synchronized
        fun addListener(listener: IListener): Int {
            Log.i("ServerService", "Adding Listener: $listener")
            listeners[nextListenerId] = listener
            return nextListenerId++
        }

        @Synchronized
        fun sendEvent(fromId: Int, event: String) {
            Log.i("ServerService", "Sending \"$event\" from $fromId")
            resurrectClients(event)

            val deadIds = mutableSetOf<Int>()
            listeners.forEach { (id, listener) ->
                if (listener.asBinder().pingBinder()) {
                    try {
                        if (id != fromId) listener.onEvent(event)
                    } catch (e: RemoteException) {
                        deadIds.add(id)
                    }
                } else {
                    deadIds.add(id)
                }
            }

            deadIds.forEach { listeners.remove(it) }
        }
    }

    class ServerServiceImpl(private val clients: Clients) : IServerService.Stub() {
        init {
            Log.i("ServerService", "Creating new ServerServiceImpl")
        }

        override fun addListener(listener: IListener): Int = clients.addListener(listener)

        override fun sendEvent(id: Int, event: String) = clients.sendEvent(id, event)
    }
}
