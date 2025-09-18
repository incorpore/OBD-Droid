package prot;

import java.util.EventListener;

/**
 * TelegramWriter
 * Interface to handle outgoing protocol Telegrams
 */
public interface TelegramWriter extends EventListener
{
	/**
	 * handle outgoing protocol telegram
	 *
	 * @param buffer - telegram buffer
	 * @return number of bytes sent
	 */
	int writeTelegram(char[] buffer);

	/**
	 * handle outgoing protocol telegram
	 *
	 * @param buffer - telegram buffer
	 * @param type   telegram type (numeric ID)
	 * @param id     unique telegram ID (Sequence number) may be null to generate automatic
	 * @return number of bytes sent
	 */
	int writeTelegram(char[] buffer, int type, Object id);

}
