/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
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
package services.moleculer.stream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Promise;
import services.moleculer.error.MoleculerClientError;
import services.moleculer.error.MoleculerError;

/**
 * !!! This package are in development phase !!!
 */
public class PacketStream {

	// --- CONSTANTS ---

	protected static final int DEFAULT_MIN_PACKET_SIZE = 1024 * 16;

	protected static final byte[] CLOSE_MARKER = new byte[0];

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(PacketStream.class);

	// --- COMPONENTS ---

	protected final ScheduledExecutorService scheduler;

	// --- VARIABLES ---

	/**
	 * Current node ID.
	 */
	protected final String nodeID;

	/**
	 * Is stream closed?
	 * 
	 * @see isClosed
	 */
	protected final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Memory-buffer of an unconnected stream. Connected streams do not use this
	 * buffer.
	 */
	protected final LinkedList<byte[]> buffer = new LinkedList<>();

	/**
	 * Counter of transfered bytes.
	 * 
	 * @see getTransferedBytes
	 */
	protected final AtomicLong transferedBytes = new AtomicLong();

	/**
	 * Array of PacketListeners (receivers).
	 */
	protected PacketListener[] listeners;

	/**
	 * Cause (blocking error).
	 */
	protected Throwable cause;

	/**
	 * Minimum size of packets.
	 */
	protected int packetSize = DEFAULT_MIN_PACKET_SIZE;

	/**
	 * Time between each packet sent. This may be necessary because the other
	 * Threads will get some CPU-time.
	 */
	protected long packetDelay = 10;

	// --- CONSTRUCTOR ---

	public PacketStream(String nodeID, ScheduledExecutorService scheduler) {
		this.nodeID = nodeID;
		this.scheduler = scheduler;
	}

	// --- SET EVENT LISTENER ---

	public boolean onPacket(PacketListener listener) {
		if (listener == null) {
			return false;
		}
		if (listeners == null) {
			listeners = new PacketListener[] { listener };
		} else {
			for (PacketListener test : listeners) {
				if (test == listener) {
					return false;
				}
			}
			PacketListener[] copy = new PacketListener[listeners.length];
			System.arraycopy(listeners, 0, copy, 0, listeners.length);
			copy[listeners.length] = listener;
			listeners = copy;
		}
		try {
			if (cause == null) {
				for (byte[] bytes : buffer) {
					if (bytes == CLOSE_MARKER) {
						listener.onPacket(null, null, true);
					} else {
						listener.onPacket(bytes, null, false);
					}
				}
			} else {
				listener.onPacket(null, cause, true);
			}
		} catch (MoleculerError moleculerError) {
			throw moleculerError;
		} catch (Throwable error) {
			throw new MoleculerClientError("Unable to send packet to stream listener!", error, nodeID, null);
		}
		return true;
	}

	// --- SEND BYTES ---

	public boolean sendData(byte[] bytes) {
		if (bytes != null && bytes.length > 0 && !closed.get()) {
			try {
				if (listeners == null) {
					buffer.addLast(bytes);
				} else if (listeners.length == 1) {
					listeners[0].onPacket(bytes, null, false);
				} else {
					for (PacketListener listener : listeners) {
						listener.onPacket(bytes, null, false);
					}
				}
				transferedBytes.addAndGet(bytes.length);
			} catch (MoleculerError moleculerError) {
				throw moleculerError;
			} catch (Throwable error) {
				throw new MoleculerClientError("Unable to send bytes to stream listener!", error, nodeID, null);
			}
			return true;
		}
		return false;
	}

	// --- SEND ERROR ---

	public boolean sendError(Throwable cause) {
		if (cause == null) {
			throw new IllegalArgumentException("Unable to send \"null\" as Exception!");
		}
		if (closed.compareAndSet(false, true)) {
			this.cause = cause;
			if (listeners != null) {
				try {
					if (listeners.length == 1) {
						listeners[0].onPacket(null, cause, true);
					} else {
						for (PacketListener listener : listeners) {
							listener.onPacket(null, cause, true);
						}
					}
				} catch (MoleculerError moleculerError) {
					throw moleculerError;
				} catch (Throwable error) {
					throw new MoleculerClientError("Unable to send error to stream listener!", error, nodeID, null);
				}
			}
			return true;
		}
		return false;
	}

	// --- SEND CLOSE MARKER ---

	public boolean sendClose() {
		if (closed.compareAndSet(false, true)) {
			try {
				if (listeners == null) {
					buffer.addLast(CLOSE_MARKER);
				} else if (listeners.length == 1) {
					listeners[0].onPacket(null, cause, true);
				} else {
					for (PacketListener listener : listeners) {
						listener.onPacket(null, cause, true);
					}
				}
			} catch (Throwable error) {
				logger.warn("Unable to send close marker to stream listener!", error);
				return false;
			}
			return true;
		}
		return false;
	}

	// --- ACT AS OUTPUT STREAM ---

	public OutputStream asOutputStream() {
		OutputStream out = new OutputStream() {

			@Override
			public final void write(int b) {
				sendData(new byte[] { (byte) b });
			}

			@Override
			public final void write(byte[] b) {
				write(b, 0, b.length);
			}

			@Override
			public final void write(byte[] b, int off, int len) {
				byte[] copy = new byte[len];
				System.arraycopy(b, 0, copy, 0, len);
				sendData(copy);
			}

			@Override
			public final void flush() {

				// Do nothing
			}

			@Override
			public final void close() {
				sendClose();
			}

		};

		// Bufferless/direct output
		if (packetSize < 2) {
			return out;
		}

		// Buffered output
		return new BufferedOutputStream(out, packetSize);
	}

	// --- ACT AS BYTE CHANNEL ---

	public WritableByteChannel asWritableByteChannel() {

		// Bufferless/direct output
		if (packetSize < 2) {
			return new WritableByteChannel() {

				@Override
				public final boolean isOpen() {
					return !closed.get();
				}

				@Override
				public final void close() {
					sendClose();
				}

				@Override
				public final int write(ByteBuffer src) {
					try {
						int len = src.remaining();
						if (len > 0) {
							byte[] packet = new byte[len];
							src.get(packet);
							sendData(packet);
						}
						return len;
					} catch (Throwable cause) {
						try {
							sendError(cause);
						} catch (Throwable ignored) {
						}
						throw cause;
					}
				}

				@Override
				protected final void finalize() throws Throwable {
					close();
				}

			};
		}

		// Buffered output
		final OutputStream out = asOutputStream();
		return new WritableByteChannel() {

			@Override
			public final boolean isOpen() {
				return !closed.get();
			}

			@Override
			public final void close() throws IOException {
				out.flush();
				out.close();
			}

			@Override
			public final int write(ByteBuffer src) throws IOException {
				try {
					int len = src.remaining();
					if (len > 0) {
						byte[] packet = new byte[len];
						src.get(packet);
						out.write(packet);
					}
					return len;
				} catch (Throwable cause) {
					try {
						sendError(cause);
					} catch (Throwable ignored) {
					}
					throw cause;
				}
			}

		};
	}

	// --- "TRANSFER FROM" METHODS ---

	public Promise transferFrom(File source) {
		try {
			return transferFrom(new FileInputStream(source));
		} catch (Throwable cause) {
			return Promise.reject(cause);
		}
	}

	public Promise transferFrom(InputStream source) {
		byte[] packet = new byte[packetSize < 1 ? DEFAULT_MIN_PACKET_SIZE : packetSize];
		Promise promise = new Promise();
		OutputStream destination = asOutputStream();
		scheduleNextPacket(source, destination, promise, packet);
		return promise;
	}

	public Promise transferFrom(ReadableByteChannel source) {
		ByteBuffer packet = ByteBuffer.allocate(packetSize < 1 ? DEFAULT_MIN_PACKET_SIZE : packetSize);
		Promise promise = new Promise();
		OutputStream destination = asOutputStream();
		scheduleNextPacket(source, destination, promise, packet);
		return promise;
	}

	protected void scheduleNextPacket(ReadableByteChannel source, OutputStream destination, Promise promise,
			ByteBuffer packet) {
		scheduler.schedule(() -> {
			try {
				packet.rewind();
				int len = source.read(packet);
				if (len < 0) {
					try {
						try {
							source.close();
						} catch (Throwable ignored) {
						}
						try {
							destination.close();
						} catch (Throwable ignored) {
						}
					} finally {
						promise.complete();
					}
				} else if (len == 0) {
					scheduleNextPacket(source, destination, promise, packet);
				} else {
					byte[] copy = new byte[len];
					System.arraycopy(packet.array(), 0, copy, 0, len);
					destination.write(copy);
					scheduleNextPacket(source, destination, promise, packet);
				}
			} catch (Throwable cause) {
				try {
					try {
						source.close();
					} catch (Throwable ignored) {
					}
					try {
						destination.close();
					} catch (Throwable ignored) {
					}
					try {
						sendError(cause);
					} catch (Throwable ignored) {
					}
				} finally {
					promise.complete(cause);
				}
			}
		}, packetDelay, TimeUnit.MILLISECONDS);
	}

	protected void scheduleNextPacket(InputStream source, OutputStream destination, Promise promise, byte[] packet) {
		scheduler.schedule(() -> {
			try {
				int len = source.read(packet);
				if (len < 0) {
					try {
						try {
							source.close();
						} catch (Throwable ignored) {
						}
						try {
							destination.close();
						} catch (Throwable ignored) {
						}
					} finally {
						promise.complete();
					}
				} else if (len == 0) {
					scheduleNextPacket(source, destination, promise, packet);
				} else {
					byte[] copy = new byte[len];
					System.arraycopy(packet, 0, copy, 0, len);
					destination.write(copy);
					scheduleNextPacket(source, destination, promise, packet);
				}
			} catch (Throwable cause) {
				try {
					try {
						source.close();
					} catch (Throwable ignored) {
					}
					try {
						destination.close();
					} catch (Throwable ignored) {
					}
					try {
						sendError(cause);
					} catch (Throwable ignored) {
					}
				} finally {
					promise.complete(cause);
				}
			}
		}, packetDelay, TimeUnit.MILLISECONDS);
	}

	// --- "TRANSFER TO" METHODS ---

	public Promise transferTo(File destination) {
		try {
			return transferTo(new FileOutputStream(destination));
		} catch (Throwable cause) {
			return Promise.reject(cause);
		}
	}

	public Promise transferTo(OutputStream destination) {
		return new Promise(res -> {
			onPacket((bytes, cause, close) -> {

				// Data received
				if (bytes != null) {
					try {
						destination.write(bytes);
					} catch (Throwable err) {
						cause = err;
					}
				}

				// Close received
				if (close) {
					try {
						destination.flush();
						destination.close();
						if (cause == null) {
							res.resolve();
						}
					} catch (Throwable error) {
						cause = error;
					}
				}

				// Error received
				if (cause != null) {
					res.reject(cause);
				}

			});
		});
	}

	public Promise transferTo(WritableByteChannel destination) {
		return new Promise(res -> {
			onPacket((bytes, cause, close) -> {

				// Data received
				if (bytes != null) {
					try {
						ByteBuffer buffer = ByteBuffer.wrap(bytes);
						int pos = 0;
						while (buffer.hasRemaining()) {
							pos += destination.write(buffer);
							buffer.position(pos);
						}
					} catch (Throwable err) {
						cause = err;
					}
				}

				// Close received
				if (close) {
					try {
						destination.close();
						if (cause == null) {
							res.resolve();
						}
					} catch (Throwable error) {
						cause = error;
					}
				}

				// Error received
				if (cause != null) {
					res.reject(cause);
				}

			});
		});
	}

	// --- GETTERS AND SETTERS ---

	public int getPacketSize() {
		return packetSize;
	}

	public void setPacketSize(int packetSize) {
		this.packetSize = packetSize < 0 ? 0 : packetSize;
	}

	public long getPacketDelay() {
		return packetDelay;
	}

	public void setPacketDelay(long packetDelay) {
		this.packetDelay = packetDelay < 0 ? 0 : packetDelay;
	}

	public boolean isClosed() {
		return closed.get();
	}

	public long getTransferedBytes() {
		return transferedBytes.get();
	}

}