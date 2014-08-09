import java.util.*;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

/**
 * Uses regular key-value pairs for counting instead of a bitarray. This introduces a space overhead but allows
 * distribution of keys, thus increasing throughput. Pipelining can also be leveraged in this approach to minimize
 * network latency.
 * 
 * @param <T>
 */
public class RedisCountBloomFilter<T> extends CountBloomFilter<T> {
	final static String BLOOM = "cbloomfilter";
	final static String M = BLOOM + ":m";
	final static String K = BLOOM + ":k";
	final static String C = BLOOM + ":c";
	final static String HASH = BLOOM + ":hash";
	final static String COUNTS = BLOOM + ":counts";
	protected Jedis jedis;
	protected Pipeline p;
	protected int ttl = 0;

	public RedisCountBloomFilter(Jedis jedis, double n, double p) {
		this(jedis, optimalM(n, p), optimalK(n, optimalM(n, p)));
	}

	public RedisCountBloomFilter(Jedis jedis, int m, int k) {
		this(jedis, null, new RedisBitSet(jedis, BLOOM, m), m, k, 32);
	}

	protected RedisCountBloomFilter(Jedis jedis, AdvanceBitSet counts, AdvanceBitSet bloom, int m, int k, int c) {
		super(counts, bloom, m, k, c);
		this.jedis = jedis;
		// Create the bloomfilter metadata in Redis. If it is done concurrently --> abort
		jedis.watch(M);
		if (!jedis.exists(M)) {
			Transaction t = jedis.multi();
			t.set(M, Integer.toString(m));
			t.set(K, Integer.toString(k));
			t.set(C, Integer.toString(c));
			t.set(HASH, getCryptographicHashFunctionName());
			t.exec();
		}
		jedis.unwatch();
	}

	/**
	 * Allows Redis to discard inserted objects after <tt>ttl</tt> seconds. However, there is no guarantee that the
	 * database actually removes these values. For deterministic removal {@link #remove(byte[])} should still be called.
	 * 
	 * @param ttl
	 *            the timespan in seconds after which Redis may discard an inserted element
	 */
	public void makeTransient(int ttl) {
		this.ttl = ttl;
	}

	@Override
	protected void increment(int index) {
		String key = toKey(index);
		p.incr(key);
		if (ttl != 0)
			p.expire(key, ttl);
	}

	@Override
	protected void decrement(int index) {
		p.decr(toKey(index));
	}

	@Override
	public void remove(byte[] value) {
		int[] hashes = hash(value);
		String[] hashKeys = toKeys(hashes);
		lock();
		List<String> result = null;
		result = jedis.mget(hashKeys);
		begin();
		for (int i = 0; i < k; i++) {
			Long v = Long.valueOf(result.get(i));
			if (v <= 1) {
				setBit(hashes[i], false);
			}
			decrement(hashes[i]);
		}
		if (commit() == null) {
			remove(value);
			return;
		}
		unlock();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void clear() {
		getBloom().clear();
		begin();
		p.keys(BLOOM + "*");
		List<Object> result = commit();
		if (result != null) {
			begin();
			// Remove all matching keys
			for (String key : (Set<String>) result.get(0)) {
				p.del(key);
			}
			commit();
		}
	}

	@Override
	public boolean add(byte[] value) {
		lock();
		int[] hashes = hash(value);
		begin();
		for (int position : hashes) {
			setBit(position);
			increment(position);
		}
		if (commit() == null) {
			return add(value);
		}
		unlock();
		return true;
	}

	@Override
	public List<Boolean> addAll(Collection<T> values) {
        List<Boolean> added = new ArrayList<Boolean>();
		begin();
		for (T val : values) {
			super.add(val.toString().getBytes(getDefaultCharset()));
		}
		if (commit() == null)
			addAll(values);

        return added;    // TODO - implement the check (and tests!)
	}

	@Override
	public boolean contains(byte[] value) {
		return getBloom().allSet(hash(value));
		// Alternative implementation: get all counters and check if they're > 0
	}

	protected String[] toKeys(int... hashes) {
		String[] watched = new String[hashes.length];
		for (int i = 0; i < watched.length; i++) {
			watched[i] = toKey(hashes[i]);
		}
		return watched;
	}

	protected void lock() {
		while (!setLock()) {
			try {
				Thread.sleep((int) (Math.random() * 5));
			} catch (InterruptedException e) {
			}
		}
	}

	protected boolean setLock() {
		Object result = jedis.eval("local lock = redis.call('get', KEYS[1])\r\n" + 
				"if not lock then\r\n" + 
				"	return redis.call('setex', KEYS[1], ARGV[1], \"locked\")\r\n" + 
				"end\r\n" + 
				"return false", 1, "mylock", "1");
		return result != null;
	}

	protected void unlock() {
		jedis.del("mylock");
	}

	protected void begin() {
		p = jedis.pipelined();
		p.multi();
		getBloom().useContext(p);
	}

	protected List<Object> commit() {
		Response<List<Object>> result = p.exec();
		p.sync();
		getBloom().leaveContext();
		return result.get();
	}

	protected RedisBitSet getBloom() {
		return (RedisBitSet) bloom;
	}

	private String toKey(int index) {
		return BLOOM + index;
	}

}
