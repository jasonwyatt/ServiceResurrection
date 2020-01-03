package us.jwf.serviceresurrection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    var isActivityBoundToServer = false
    var isActivityListeningToServer = false
    var isActivityBoundToClient = false
    var isClientBoundToServer = false
    var isClientListeningToServer = false
    var server: IServerService? = null
    var client: IClientService? = null
    var listenerId: Int? = null

    lateinit var activityServerBinding: Button
    lateinit var activityClientBinding: Button
    lateinit var listenFromActivityToServer: Button
    lateinit var sendFromActivityToServer: Button

    lateinit var clientServerBinding: Button
    lateinit var listenFromClientToServer: Button
    lateinit var sendFromClientToServer: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityServerBinding = findViewById<Button>(R.id.bind_activity_to_server).apply {
            setOnClickListener {
                if (isActivityBoundToServer) {
                    unbindActivityFromServer()
                } else {
                    bindActivityToServer()
                }
                isActivityBoundToServer = !isActivityBoundToServer
            }
        }

        activityClientBinding = findViewById<Button>(R.id.bind_activity_to_client).apply {
            setOnClickListener {
                if (isActivityBoundToClient) {
                    unbindActivityFromClient()
                } else {
                    bindActivityToClient()
                }
                isActivityBoundToClient = !isActivityBoundToClient
            }
        }

        listenFromActivityToServer = findViewById<Button>(R.id.make_activity_listen_to_server).apply {
            setOnClickListener {
                if (!isActivityListeningToServer) {
                    listenerId = server?.addListener(listener)?.also {
                        Toast.makeText(
                            this@MainActivity,
                            "Listening with id: $it",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    sendFromActivityToServer.isEnabled = true
                    isEnabled = false
                }
                isActivityListeningToServer = true
            }
        }

        var helloCount = 0
        sendFromActivityToServer = findViewById<Button>(R.id.send_from_activity_to_server).apply {
            setOnClickListener {
                Log.i("MainActivity", "Send From Activity -> Server clicked.")
                listenerId?.let {
                    Log.i("MainActivity", "Sending to the server: $server")
                    server?.sendEvent(it, "Hello from Activity ${helloCount++}")
                }
            }
        }

        clientServerBinding = findViewById<Button>(R.id.bind_client_to_server).apply {
            setOnClickListener {
                client?.let {
                    if (isClientBoundToServer) {
                        clientServerBinding.setText(R.string.bind_client_to_server)
                        try { it.unbindFromServer() } catch (e: Exception) {  }
                    } else {
                        clientServerBinding.setText(R.string.unbind_client_to_server)
                        it.bindToServer()
                    }
                    isClientBoundToServer = !isClientBoundToServer
                    listenFromClientToServer.isEnabled = isClientBoundToServer
                }
            }
        }

        listenFromClientToServer = findViewById<Button>(R.id.make_client_listen_to_server).apply {
            setOnClickListener {
                client?.let {
                    //if (!isClientListeningToServer) {
                        it.registerListenerWithServer()
                        isClientListeningToServer = true
                        listenFromClientToServer.isEnabled = false
                        sendFromClientToServer.isEnabled = true
                    //}
                }
            }
        }

        sendFromClientToServer = findViewById<Button>(R.id.send_from_client_to_server).apply {
            setOnClickListener {
                client?.sendEventToServer("Hello from Client")
            }
        }
    }

    private fun bindActivityToServer() {
        bindService(
            Intent(this, ServerService::class.java),
            serverConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun unbindActivityFromServer() {
        unbindService(serverConnection)
        server = null
        activityServerBinding.setText(R.string.bind_activity_to_server)
        isActivityListeningToServer = false
        listenFromActivityToServer.isEnabled = false
        sendFromActivityToServer.isEnabled = false
        listenerId = null
    }

    private fun bindActivityToClient() {
        bindService(
            Intent(this, ClientService::class.java),
            clientConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun unbindActivityFromClient() {
        try { client?.kill() } catch (e: RemoteException) {  }
        client = null
        activityClientBinding.setText(R.string.bind_activity_to_client)
        clientServerBinding.isEnabled = false
        listenFromClientToServer.isEnabled = false
        isClientListeningToServer = false
        sendFromClientToServer.isEnabled = false
    }

    private val serverConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("MainActivity", "Activity is not bound to ServerService")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            service.linkToDeath({ Log.i("MainActivity", "ServerService connection died.") }, 0)
            Log.i("MainActivity", "Activity is bound to ServerService")
            server = IServerService.Stub.asInterface(service)
            activityServerBinding.setText(R.string.unbind_activity_to_server)
            listenFromActivityToServer.isEnabled = true
        }
    }

    private val clientConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("MainActivity", "Activity is not bound to ClientService")
            unbindService(this)
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            Log.i("MainActivity", "Activity is bound to ClientService")
            client = IClientService.Stub.asInterface(service)
            activityClientBinding.setText(R.string.unbind_activity_to_client)
            clientServerBinding.isEnabled = true
        }
    }

    private val listener = object : IListener.Stub() {
        override fun onEvent(event: String?) {
            activityServerBinding.post {
                Toast.makeText(
                    this@MainActivity,
                    "Heard Event: $event",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
