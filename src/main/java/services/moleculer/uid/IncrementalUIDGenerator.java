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
package services.moleculer.uid;

import java.util.concurrent.atomic.AtomicLong;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;

/**
 * Fast UIDGenerator, based on nodeID and an atomic sequence number.
 * It's faster than the StandardUIDGenerator.
 * 
 * @see StandardUUIDGenerator
 */
@Name("Incremental UID Generator")
public final class IncrementalUIDGenerator extends UIDGenerator {

	// --- HOST/NODE PREFIX ---

	private char[] prefix;

	// --- SEQUENCE ---

	private final AtomicLong counter = new AtomicLong();

	// --- START GENERATOR ---

	/**
	 * Initializes UID generator instance.
	 * 
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public final void start(ServiceBroker broker, Tree config) throws Exception {
		String id = config.get("prefix", broker.nodeID());
		prefix = (id + ':').toCharArray();
	}

	// --- GENERATE UID ---

	@Override
	public final String nextUID() {
		StringBuilder tmp = new StringBuilder(32);
		tmp.append(prefix);
		tmp.append(counter.incrementAndGet());
		return tmp.toString();
	}

	// --- GETTERS / SETTERS ---

	public final String getPrefix() {
		return new String(prefix);
	}

	public final void setPrefix(String prefix) {
		this.prefix = prefix.toCharArray();
	}

}