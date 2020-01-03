package arcs.android.service.resurrection

import android.content.Context
import android.content.Intent

class ResurrectionHelper(
    private val context: Context,
    private val onResurrected: (events: List<String>) -> Unit
) {
    /**
     * Determines if the provided [intent] represents a resurrection.
     */
    fun onStartCommand(intent: Intent?) {
        if (intent?.action?.startsWith(ResurrectionRequest.ACTION_RESURRECT) == true) {
            val notifiers = intent.getStringArrayListExtra(
                ResurrectionRequest.EXTRA_RESURRECT_NOTIFIER
            ) ?: return

            onResurrected(notifiers)
        }
    }

    /**
     * Registers the client using this [ResurrectionHelper] with a [ResurrectorService].
     *
     * @param resurrectOn list of events the client would like to be resurrected for. An empty
     *     list signifies that the client is interested in being resurrected for any event.
     */
    fun requestResurrection(
        serviceClass: Class<out ResurrectorService>,
        resurrectOn: List<String> = emptyList()
    ) {
        val intent = Intent(context, serviceClass)
        val request = ResurrectionRequest.createDefault(context, resurrectOn)
        request.populateRequestIntent(intent)
        context.startService(intent)
    }
}
