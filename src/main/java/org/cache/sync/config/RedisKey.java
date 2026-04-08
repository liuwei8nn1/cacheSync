package org.cache.sync.config;

/**
 *
 * @since 2026/4/8
 */
public class RedisKey {

	public static final String KEY_SPLIT = ":";
	/**
	 * 流的key，STREAM 类型 </br>
	 *
	 * < $prefixKey:stream: > < 消息的详情 >
	 */
	public static final String KEY_STREAM = "stream";

	/**
	 * 延时消息的key，ZSET 类型 </br>
	 *
	 * < $prefixKey:delayed: >  < $messageId > < 延时时间戳 >
	 */
	public static final String KEY_DELAYED = "delayed";
	/**
	 * 延时消息的key，HASH 类型， 保存的是延时消息的详情 </br>
	 *
	 * < $prefixKey:delayed:message:messageId > < 消息的详情 KV 保存 >
	 */
	public static final String KEY_DELAYED_MESSAGE = "delayed:message";

	/**
	 * @see #KEY_DELAYED_MESSAGE
	 */
	public static String calcDelayedMessageKey(String prefixKey,String messageId) {
		return prefixKey + KEY_SPLIT + KEY_DELAYED_MESSAGE + KEY_SPLIT + messageId;
	}

	/**
	 * @see #KEY_DELAYED
	 */
	public static String calcDelayedKey(String prefixKey) {
		return prefixKey + KEY_SPLIT + KEY_DELAYED;
	}

	/**
	 * @see #KEY_STREAM
	 */
	public static String calcStreamKey(String prefixKey) {
		return prefixKey + KEY_SPLIT + KEY_STREAM;
	}

}
