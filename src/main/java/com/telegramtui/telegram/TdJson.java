package com.telegramtui.telegram;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * JNA interface to the four C functions exposed by libtdjson.
 */
public interface TdJson extends Library {

	TdJson INSTANCE = Native.load("tdjson", TdJson.class);

	long td_json_client_create();

	void td_json_client_send(long clientId, String request);

	String td_json_client_receive(long clientId, double timeout);

	void td_json_client_destroy(long clientId);

	String td_execute(String request);
}
