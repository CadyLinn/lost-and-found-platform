package com.lostfound.backend.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the K largest elements seen so far, using a min-heap of size K.
 *
 * <p>The heap's top is the <em>smallest</em> of the current top K. Each new element only needs
 * to beat that one to get in. That is O(n log k) instead of sorting everything in O(n log n),
 * and memory stays at K elements no matter how many candidates there are.
 */
public final class TopK<T> {

    private final int k;
    private final Comparator<T> order;
    private final PriorityQueue<T> heap;

    public TopK(int k, Comparator<T> order) {
        if (k <= 0) throw new IllegalArgumentException("k must be positive");
        this.k = k;
        this.order = order;
        this.heap = new PriorityQueue<>(k, order);
    }

    public void offer(T element) {
        if (heap.size() < k) {
            heap.add(element);
        } else if (order.compare(element, heap.peek()) > 0) {
            heap.poll();
            heap.add(element);
        }
    }

    /** The kept elements, best first. */
    public List<T> best() {
        List<T> result = new ArrayList<>(heap);
        result.sort(order.reversed());
        return result;
    }
}
