package pvs;

import java.util.EventListener;

/**
 * Listener interface for notification of process variable changes
 *
 
 */
public interface PvChangeListener extends EventListener
{

	/**
	 * handler for process variable changes
	 */
	void pvChanged(PvChangeEvent event);

}
