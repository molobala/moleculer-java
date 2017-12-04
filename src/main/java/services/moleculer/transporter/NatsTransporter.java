/**
 * This software is licensed under MIT license.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
 * <br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.transporter;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import io.datatree.Tree;
import io.nats.client.Connection;
import io.nats.client.ConnectionEvent;
import io.nats.client.DisconnectedCallback;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import services.moleculer.Promise;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;

/**
 * Not implemented (yet).
 */
@Name("NATS Transporter")
public final class NatsTransporter extends Transporter implements MessageHandler, DisconnectedCallback {

	// --- PROPERTIES ---

	private SSLContext sslContext;
	private String username;
	private String password;
	private boolean secure;
	private String[] urls = new String[] { "127.0.0.1" };

	// --- NATS CONNECTION ---

	private Connection connection;

	// --- CONSTUCTORS ---

	public NatsTransporter() {
		super();
	}

	public NatsTransporter(String prefix) {
		super(prefix);
	}

	public NatsTransporter(String prefix, String... urls) {
		super(prefix);
		this.urls = urls;
	}

	public NatsTransporter(String prefix, String username, String password, boolean secure, String... urls) {
		super(prefix);
		this.username = username;
		this.password = password;
		this.secure = secure;
		this.urls = urls;
	}

	// --- START TRANSPORTER ---

	/**
	 * Initializes transporter instance.
	 * 
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public final void start(ServiceBroker broker, Tree config) throws Exception {

		// Process basic properties (eg. "prefix")
		super.start(broker, config);

		// Process config
		Tree urlNode = config.get(URL);
		if (urlNode != null) {
			List<String> urlList;
			if (urlNode.isPrimitive()) {
				urlList = new LinkedList<>();
				String url = urlNode.asString().trim();
				if (!url.isEmpty()) {
					urlList.add(url);
				}
			} else {
				urlList = urlNode.asList(String.class);
			}
			if (!urlList.isEmpty()) {
				urls = new String[urlList.size()];
				urlList.toArray(urls);
			}
		}
		username = config.get("username", username);
		password = config.get(PASSWORD, password);
		secure = config.get(SECURE, secure);

		// Connect to NATS server
		connect();
	}

	// --- CONNECT ---

	private final void connect() {
		try {

			// Set NATS connection options
			Options.Builder builder = new Options.Builder();
			if (secure) {
				builder.secure();
			}
			if (username != null && password != null && !username.isEmpty() && !password.isEmpty()) {
				builder.userInfo(username, password);
			}
			if (sslContext != null) {
				builder.sslContext(sslContext);
			}
			builder.disconnectedCb(this);
			builder.noReconnect();
			Options options = builder.build();

			// Connect to NATS server
			disconnect();
			StringBuilder urlList = new StringBuilder(128);
			for (String url : urls) {
				if (urlList.length() > 0) {
					urlList.append(',');
				}
				if (url.indexOf(':') == -1) {
					url = url + ":4222";
				}
				if (!url.startsWith("nats://")) {
					url = "nats://" + url;
				}
				urlList.append(url);
			}
			connection = Nats.connect(urlList.toString(), options);
			logger.info("NATS pub-sub connection is estabilished.");
			connected();
		} catch (Exception cause) {
			String msg = cause.getMessage();
			if (msg == null || msg.isEmpty()) {
				msg = "Unable to connect to NATS server!";
			} else if (!msg.endsWith("!") && !msg.endsWith(".")) {
				msg += "!";
			}
			logger.warn(msg);
			reconnect();
		}
	}

	// --- DISCONNECT ---

	@Override
	public final void onDisconnect(ConnectionEvent event) {
		logger.info("NATS pub-sub connection disconnected.");
		reconnect();
	}

	private final void disconnect() {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (Throwable cause) {
			logger.warn("Unexpected error occured while closing NATS connection!", cause);
		} finally {
			connection = null;
		}
	}

	// --- RECONNECT ---

	private final void reconnect() {
		disconnect();
		logger.info("Trying to reconnect...");
		scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
	}

	// --- ANY I/O ERROR ---

	@Override
	protected final void error(Throwable cause) {
		logger.warn("Unexpected communication error occured!", cause);
		reconnect();
	}

	// --- STOP TRANSPORTER ---

	/**
	 * Closes transporter.
	 */
	@Override
	public final void stop() {
		disconnect();
	}

	// --- SUBSCRIBE ---

	@Override
	public final Promise subscribe(String channel) {
		if (connection != null) {
			try {
				connection.subscribe(channel, this);
			} catch (Exception cause) {
				return Promise.reject(cause);
			}
		}
		return Promise.resolve();
	}

	// --- MESSAGE RECEIVED ---

	@Override
	public final void onMessage(Message msg) {
		// System.out.println("RECEIVED: " + new String(msg.getData()));
		received(msg.getSubject(), msg.getData());
	}

	// --- PUBLISH ---

	@Override
	public final void publish(String channel, Tree message) {
		if (connection != null) {
			try {
				// System.out.println("SEND: " + message.toString(false));
				connection.publish(channel, serializer.write(message));
			} catch (Exception cause) {
				logger.warn("Unable to send message to NATS server!", cause);
				reconnect();
			}
		}
	}

	// --- GETTERS / SETTERS ---

	public final String[] getUrls() {
		return urls;
	}

	public final void setUrls(String[] urls) {
		this.urls = urls;
	}

	public final String getUsername() {
		return username;
	}

	public final void setUsername(String username) {
		this.username = username;
	}

	public final String getPassword() {
		return password;
	}

	public final void setPassword(String password) {
		this.password = password;
	}

	public final SSLContext getSslContext() {
		return sslContext;
	}

	public final void setSslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
	}

	public final boolean isSecure() {
		return secure;
	}

	public final void setSecure(boolean secure) {
		this.secure = secure;
	}

}