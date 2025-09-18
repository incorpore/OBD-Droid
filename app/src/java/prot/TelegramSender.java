package prot;

import java.util.Iterator;
import java.util.Vector;

/**
 * TelegramSender
 * list of telegram writers
 * (handlers for outgoing telegrams)
 *
 
 */
public class TelegramSender
{
	/**
	 * Handlers for TelegramWriter List
	 */

	@SuppressWarnings("rawtypes")
	private final
	Vector telegramWriters = new Vector();

	/**
	 * add a new Writer to be notified about new telegrams
	 *
	 * @param newWriter - TelegramWriter to be added
	 * @return true if adding was OK, otherwise false
	 */
	@SuppressWarnings("unchecked")
	public boolean addTelegramWriter(TelegramWriter newWriter)
	{
		return (telegramWriters.add(newWriter));
	}

	/**
	 * remove a Writer to be notified about new telegrams
	 *
	 * @param remWriter - TelegramWriter to be removed
	 * @return true if adding was OK, otherwise false
	 */
	public boolean removeTelegramWriter(TelegramWriter remWriter)
	{
		return (telegramWriters.remove(remWriter));
	}

	/**
	 * Notify all telegram Writers about new telegram
	 *
	 * @param buffer - telegram buffer
	 */
	@SuppressWarnings("rawtypes")
	private void sendTelegram(char[] buffer, int type, Object id)
	{
		Iterator it = telegramWriters.iterator();
		Object currWriter;

		ProtoHeader.log.finer(this.toString() + " TX:" + ProtUtils.hexDumpBuffer(buffer));

		while (it.hasNext())
		{
			currWriter = it.next();
			if (currWriter instanceof TelegramWriter)
			{
				((TelegramWriter) currWriter).writeTelegram(buffer, type, id);
			}
		}
	}

	/**
	 * Notify all telegram Writers about new telegram
	 *
	 * @param buffer - telegram buffer
	 */
	public void sendTelegram(char[] buffer)
	{
		sendTelegram(buffer, 0, null);
	}

}
