package services.moleculer.eventbus;

import static services.moleculer.transporter.Transporter.PACKET_EVENT;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.transporter.Transporter;

public final class RemoteListenerEndpoint extends ListenerEndpoint {

	// --- COMPONENTS ---

	private Transporter transporter;

	// --- START ENDPOINT ---

	/**
	 * Initializes Container instance.
	 * 
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public final void start(ServiceBroker broker, Tree config) throws Exception {

		// Set base properties
		super.start(broker, config);

		// Set components
		transporter = broker.components().transporter();
	}

	// --- INVOKE REMOTE LISTENER ---

	@Override
	public final void on(String name, Tree payload, Groups groups) throws Exception {
		Tree packet = new Tree();
		packet.put("ver", ServiceBroker.MOLECULER_VERSION);
		packet.put("sender", nodeID);
		packet.put("event", name);
		packet.putObject("groups", groups == null ? null : groups.groups());
		packet.putObject("data", payload);
		transporter.publish(PACKET_EVENT, nodeID, packet);
	}

	@Override
	public final boolean local() {
		return false;
	}

}