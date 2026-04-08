package org.cache.sync.core;

import java.util.HashMap;
import java.util.Map;

import org.cache.sync.config.CacheSyncProperties;
import org.springframework.data.redis.connection.stream.MapRecord;

/**
 *
 * @author LiuWei
 * @since 2026/4/8
 */
public class InternalMessage {

	public static final String CACHE_KEY = "__cacheKey";
	public static final String TYPE = "__type";
	public static final String SUB_TYPE = "__subType";
	public static final String TIMESTAMP = "__timestamp";
	public static final String RETRY_SIZE = "__retrySize";
	public static final String DELAY = "__delay";

	public String messageId;
	public String type;
	public String subType;
	public String cacheKey;
	public Integer retrySize;
	public Integer delayed;
	public Long timestamp;
	public HashMap<String, String> metadata;

	public InternalMessage() {
	}

	boolean isValid() {
		return cacheKey != null && type != null && subType != null;
	}

	static InternalMessage of(MapRecord<String, Object, Object> record) {
		InternalMessage internalMessage = new InternalMessage();
		internalMessage.messageId = record.getId().getValue();
		Map<Object, Object> messageData = record.getValue();
		// 解析消息
		internalMessage.metadata = new HashMap<>();
		int retrySize = 0;
		int delayed = 0;
		for (Map.Entry<Object, Object> dataEntry : messageData.entrySet()) {
			String key = String.valueOf(dataEntry.getKey());
			String value = String.valueOf(dataEntry.getValue());
			if (InternalMessage.CACHE_KEY.equals(key)) {
				internalMessage.cacheKey = value;
			} else if (InternalMessage.TYPE.equals(key)) {
				internalMessage.type = value;
			} else if (InternalMessage.SUB_TYPE.equals(key)) {
				internalMessage.subType = value;
			} else if (InternalMessage.TIMESTAMP.equals(key)) {
				internalMessage.timestamp = Long.parseLong(value);
			} else if (InternalMessage.RETRY_SIZE.equals(key)) {
				retrySize = Integer.parseInt(value);}
			else if (InternalMessage.DELAY.equals(key)) {
				delayed = Integer.parseInt(value);
			} else {
				internalMessage.metadata.put(key, value);
			}
		}
		internalMessage.retrySize = retrySize;
		internalMessage.delayed = delayed;
		return internalMessage;
	}


}
