package com.obddroid.core.interfaces;

import java.util.EventListener;

/**
 * TelegramListener
 * Interface to handle incoming protocol Telegrams
 */
public interface TelegramListener extends EventListener
{
	/**
	 * handle incoming protocol telegram
	 *
	 * @param buffer - telegram buffer
	 * @return number of listeners notified
	 */
	int handleTelegram(char[] buffer);
}
