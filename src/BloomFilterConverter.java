import org.apache.commons.codec.binary.Base64;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class BloomFilterConverter {
	
	/**
	 * Convert a Bloom filter into a JSON Element.
	 * @param source the Bloom filter to convert
	 * @return the JSON representation of the Bloom filter
	 */
	public static JsonElement toJson(StandardBloomFilter<?> source) {
		JsonObject root = new JsonObject();
		root.addProperty("m", source.getM());
		root.addProperty("k", source.getK());
		root.addProperty("HashMethod", source.getHashMethod().toString());
		root.addProperty("CryptographicHashFunction", source.getCryptographicHashFunctionName());
		byte[] bits = source.getBitSet().toByteArray();
		
		Base64 base64 = new Base64(true);
		root.addProperty("bits", new String(base64.encode(bits)));
		
		return root;
	}
		
	public static StandardBloomFilter<String> fromJson(JsonElement source) {
		return fromJson(source, String.class);
	}
	
	/**
	 * Constructs a Bloom filter from its JSON representation
	 * 
	 * @param source the JSON source
	 * @param type Generic type parameter of the Bloom filter
	 * @return the Bloom filter
	 */
	public static <T> StandardBloomFilter<T> fromJson(JsonElement source, Class<T> type) {
		JsonObject root = source.getAsJsonObject();
		int m = root.get("m").getAsInt();
		int k = root.get("k").getAsInt();
		String hashMethod = root.get("HashMethod").getAsString();
		String hashFunctionName = root.get("CryptographicHashFunction").getAsString();
		byte[] bits = null;
		
		bits =  Base64.decodeBase64(root.get("bits").getAsString());
		StandardBloomFilter<T> bf = new StandardBloomFilter<T>(AdvanceBitSet.valueOf(bits), m, k, StandardBloomFilter.HashMethod.Murmur,
				hashFunctionName);
		bf.setHashMethod(StandardBloomFilter.HashMethod.valueOf(hashMethod));
		return bf;
	}
	

}
