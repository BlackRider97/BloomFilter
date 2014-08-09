import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;

import redis.clients.jedis.Jedis;


public class TestBloomFilter {
	
	private static void testStandardBF()
	{
		double expectedNoOfElements = 2000000;
		double tolerableFalsePositiveRate = 0.1;
		double precentageOfActualInsertation = 0.9;
		String testString = "testString";
		
		StandardBloomFilter<String> bf = new StandardBloomFilter<String>(expectedNoOfElements, tolerableFalsePositiveRate);
		
		int numberOfActualInsert = (int)(expectedNoOfElements * precentageOfActualInsertation);
		for( int i=0 ; i < numberOfActualInsert; i++ )
		{
			bf.add(testString+i);
		}
		
		System.out.println(bf.contains(testString+"1000"));
		
		int countFalsePositive = 0;
		for( int i=0 ; i < (int)(expectedNoOfElements); i++ )
		{
			boolean result = bf.contains("falseString"+i);
			if(result){
				countFalsePositive++;
			}
		}
		System.out.println("False positive rate="+ (countFalsePositive/expectedNoOfElements));
	}
	
	
	private static void testRedisCBF()
	{

        String host = "localhost";
        int port = 6379;
        
		double expectedNoOfElements = 1000000;
		double tolerableFalsePositiveRate = 0.1;
		double precentageOfActualInsertation = 0.9;
		String testString = "testString";
		
	       // remove if already exists
		RedisCountBloomFilter<String> bf = new RedisCountBloomFilter<String>(new Jedis(host, port), expectedNoOfElements, tolerableFalsePositiveRate);
		
		int numberOfActualInsert = (int)(expectedNoOfElements * precentageOfActualInsertation);
		for( int i=0 ; i < numberOfActualInsert; i++ )
		{
			bf.add(testString+i);
		}
		
		System.out.println(bf.contains(testString+"1000"));
		
		int countFalsePositive = 0;
		for( int i=0 ; i < (int)(expectedNoOfElements); i++ )
		{
			boolean result = bf.contains("falseString"+i);
			if(result){
				countFalsePositive++;
			}
		}
		System.out.println("False positive rate="+ (countFalsePositive/expectedNoOfElements));
	}
	
	private static void testRedisBF()
	{

        String host = "localhost";
        int port = 6379;

        String name = "loadExistingTest";
        
		double expectedNoOfElements = 2000000;
		double tolerableFalsePositiveRate = 0.1;
		double precentageOfActualInsertation = 0.9;
		String testString = "testString";
		
	       // remove if already exists
        RedisBloomFilter.removeFilter(new Jedis(host, port), name);
        RedisBloomFilter<String> bf =  RedisBloomFilter.createFilter(new Jedis(host, port), name, expectedNoOfElements, tolerableFalsePositiveRate);
		
		int numberOfActualInsert = (int)(expectedNoOfElements * precentageOfActualInsertation);
		for( int i=0 ; i < numberOfActualInsert; i++ )
		{
			bf.add(testString+i);
		}
		
		System.out.println(bf.contains(testString+"1000"));
		
		int countFalsePositive = 0;
		for( int i=0 ; i < (int)(expectedNoOfElements); i++ )
		{
			boolean result = bf.contains("falseString"+i);
			if(result){
				countFalsePositive++;
			}
		}
		System.out.println("False positive rate="+ (countFalsePositive/expectedNoOfElements));
	}

	public static void main(String[] args) {

		//testStandardBF();
		//testRedisBF();
		testRedisCBF();
	}


}
