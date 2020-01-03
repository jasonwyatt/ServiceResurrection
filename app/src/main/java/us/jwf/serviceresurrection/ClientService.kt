package us.jwf.serviceresurrection

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import arcs.android.service.resurrection.ResurrectionHelper

class ClientService : Service() {
    private val connection = Connection(this)
    private val resurrectionHelper: ResurrectionHelper by lazy {
        ResurrectionHelper(this, ::onResurrection)
    }

    private var wantToReListen = false
    private val listener = Listener()
    private var listenerId: Int? = null
    internal var service: IServerService? = null
        set(value) {
            field = value
            if (value != null && wantToReListen) {
                listenerId = value.addListener(Listener())
                wantToReListen = false
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        resurrectionHelper.onStartCommand(intent)
        return START_NOT_STICKY
    }

    private fun onResurrection(events: List<String>) {
        service?.let {
            if (it.asBinder().pingBinder()) return
        }

        Log.i(
            "ClientService",
            "I AM ALIVE AGAIN!!!! Received ${events.size} event(s): ${
                events.joinToString(prefix = "[", postfix = "]")
            }"
        )
        wantToReListen = true
        bindService(createIntent(), connection, Context.BIND_EXTERNAL_SERVICE)
        events.forEach(listener::onEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("ClientService", "I'm dying........")
        Process.killProcess(Process.myPid())
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i("ClientService", "Rebinding.")
    }

    override fun onBind(intent: Intent?): IBinder? = Binder()

    internal fun createIntent(): Intent = Intent(this@ClientService, ServerService::class.java)

    inner class Binder : IClientService.Stub() {
        override fun kill() {
            Process.killProcess(Process.myPid())
        }

        override fun bindToServer() {
            bindService(createIntent(), connection, Context.BIND_AUTO_CREATE)
        }

        override fun registerListenerWithServer() {
            if (listenerId == null) {
                listenerId = service?.addListener(listener)
            }
            resurrectionHelper.requestResurrection(ServerService::class.java)
        }

        override fun sendEventToServer(event: String) {
            listenerId?.let { id -> service?.sendEvent(id, event) }
        }

        override fun unbindFromServer() {
            unbindService(connection)
        }
    }

    class Listener : IListener.Stub() {
        override fun onEvent(event: String?) {
            Log.i("ClientService", "Received event: \"$event\"")
        }
    }

    class Connection(val parent: ClientService) : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("ClientService", "Disconnected from ServerService")
            parent.service = null
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.i("ClientService", "Binding Died")
            parent.service = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            Log.i("ClientService", "Connected to ServerService")
            parent.service = IServerService.Stub.asInterface(service)
        }
    }
}
