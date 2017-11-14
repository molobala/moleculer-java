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
package services.moleculer.strategy;

import java.util.concurrent.atomic.AtomicLong;

import services.moleculer.service.ActionEndpoint;
import services.moleculer.service.Name;

/**
 * XORSHIFT-based pseudorandom invocation strategy.
 * 
 * @see RoundRobinStrategy
 * @see NanoSecRandomStrategy
 * @see SecureRandomStrategy
 */
@Name("XORSHIFT Pseudorandom Strategy")
public final class XORShiftRandomStrategy extends ArrayBasedStrategy {

	// --- PROPERTIES ---

	private final AtomicLong rnd = new AtomicLong(System.nanoTime());

	// --- CONSTRUCTOR ---
	
	public XORShiftRandomStrategy(boolean preferLocal) {
		super(preferLocal);
	}
	
	// --- GET NEXT ACTION CONTAINER ---

	@Override
	public final ActionEndpoint next() {

		// Generate pseudo random
		long start, next;
		do {
			start = rnd.get();
			next = start + 1;
			next ^= (next << 21);
			next ^= (next >>> 35);
			next ^= (next << 4);
		} while (!rnd.compareAndSet(start, next));

		// Return ActionEndpoint
		return actions[(int) Math.abs(next % actions.length)];
	}

}