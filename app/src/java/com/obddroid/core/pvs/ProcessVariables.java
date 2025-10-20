package com.obddroid.core.pvs;

import java.io.Serializable;
import java.util.Collections;
import java.util.EventListener;
import java.util.EnumSet;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Aggregates the legacy process-variable primitives used across the OBD core.
 * <p>
 * Keeping everything under a single namespace makes future refactors
 * (generics, stronger typing, thread-safety improvements) easier to stage.
 * Existing code now references nested classes such as
 * {@code ProcessVariables.ProcessVar}.
 */
public final class ProcessVariables {

    private ProcessVariables() {
        // Utility holder – not instantiable.
    }

    /**
     * Contract for components that can ingest a raw map of PV attributes.
     * Historically used by {@link PvList} when protocol frames are decoded.
     */
    interface DataMapHandler {
        /**
         * handle a set/map of data attributes
         *
         * @param data Map of new data attributes to handle
         * @return previous value of corresponding data item
         */
        @SuppressWarnings("rawtypes")
        Object handleData(Map data);
    }

    /**
     * Listener that receives {@link PvChangeEvent} notifications when a PV
     * or one of its children mutates.
     */
    public interface PvChangeListener extends EventListener {

        /**
         * handler for process variable changes
         */
        void pvChanged(PvChangeEvent event);

    }

    /**
     * Listener bridge that consumes the modern {@link PvChange} payload while
     * still satisfying the legacy {@link PvChangeEvent}-based contract.
     */
    public interface TypedPvChangeListener extends PvChangeListener {

        void pvChanged(PvChange change);

        @Override
        default void pvChanged(PvChangeEvent event) {
            pvChanged(PvChange.fromEvent(event));
        }
    }

    /**
     * Change notification emitted by {@link ProcessVar} instances.
     */
    public static class PvChangeEvent extends EventObject {

        private static final long serialVersionUID = 4378855847270229897L;
        public static final int PV_NOACTION = 0x00; 							/**< NO specified PV action */
        public static final int PV_ADDED = 0x01; 									/**< new Process var was added */
        public static final int PV_DELETED = 0x02; 								/**< process var was deleted */
        public static final int PV_MODIFIED = 0x04; 							/**< process var was modified */
        public static final int PV_CONFIRMED = 0x08; 							/**< process var was confirmed */
        public static final int PV_MANUAL_MOD = 0x10; 						/**< process var was modified manually */
        public static final int PV_CLEARED = 0x20; 								/**< process var was cleared */
        public static final int PV_ERROR = 0x40; 									/**< process var has an error */
        public static final int PV_ELIMINATED = 0x80; 						/**< process var got eliminated */
        public static final int PV_CHILDCHANGE = 0x8000000; 			/**< child process var change */
        private static final int PV_ALLACTIONS = ~PV_CHILDCHANGE;	/**< mask for all actions */
        public static final int PV_ALLEVENTS = 0xFFFFFFFF; 				/**< mask for all change types */
        private int type = PV_MODIFIED;
        private Object key = ProcessVar.DEF_KEYNAME;
        private Object value = ProcessVar.DEF_KEYNAME;
        private long time = System.currentTimeMillis();

        public PvChangeEvent(Object source, Object key, Object value, int type) {
            super(source);
            setType(type);
            setKey(key);
            setValue(value);
        }

        public int getType() {
            return (type & PV_ALLACTIONS);
        }

        public boolean isChildEvent() {
            return (type & PV_CHILDCHANGE) != 0;
        }

        private void setType(int newType) {
            type = newType;
        }

        private void setKey(Object newKey) {
            key = newKey;
        }

        public Object getKey() {
            return (key);
        }

        private void setValue(Object newValue) {
            value = newValue;
        }

        public Object getValue() {
            return (value);
        }

        /** return String Representation of Event */
        @Override
        public String toString() {
            return (String.valueOf(getType()) + ":" + getKey() + "=" + getValue());
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }
    }

    /**
     * Enumerates the high-level change categories surfaced by {@link PvChange}.
     * The constants are ordered by precedence so {@link PvChange#getPrimaryType()}
     * yields a stable, meaningful default.
     */
    public enum PvChangeType {
        ADDED(PvChangeEvent.PV_ADDED),
        MODIFIED(PvChangeEvent.PV_MODIFIED),
        REMOVED(PvChangeEvent.PV_DELETED),
        CLEARED(PvChangeEvent.PV_CLEARED),
        CONFIRMED(PvChangeEvent.PV_CONFIRMED),
        MANUAL_OVERRIDE(PvChangeEvent.PV_MANUAL_MOD),
        ERROR(PvChangeEvent.PV_ERROR),
        ELIMINATED(PvChangeEvent.PV_ELIMINATED),
        NONE(PvChangeEvent.PV_NOACTION);

        private final int legacyMask;

        PvChangeType(int legacyMask) {
            this.legacyMask = legacyMask;
        }

        int getLegacyMask() {
            return legacyMask;
        }
    }

    /**
     * Modernised change payload used by {@link TypedPvChangeListener} to access
     * structured information about PV updates without relying on bitmasks.
     */
    public static final class PvChange {
        private final ProcessVar source;
        private final Object key;
        private final Object value;
        private final Set<PvChangeType> types;
        private final PvChangeType primaryType;
        private final boolean childChange;
        private final long timestamp;
        private final int legacyMask;

        private PvChange(ProcessVar source,
                         Object key,
                         Object value,
                         Set<PvChangeType> types,
                         PvChangeType primaryType,
                         boolean childChange,
                         long timestamp,
                         int legacyMask) {
            this.source = source;
            this.key = key;
            this.value = value;
            this.types = types;
            this.primaryType = primaryType;
            this.childChange = childChange;
            this.timestamp = timestamp;
            this.legacyMask = legacyMask;
        }

        public static PvChange fromEvent(PvChangeEvent event) {
            if (event == null) {
                throw new IllegalArgumentException("event == null");
            }
            int fullMask = event.isChildEvent()
                ? event.getType() | PvChangeEvent.PV_CHILDCHANGE
                : event.getType();

            EnumSet<PvChangeType> mappedTypes = EnumSet.noneOf(PvChangeType.class);
            PvChangeType primary = PvChangeType.NONE;

            for (PvChangeType candidate : PvChangeType.values()) {
                if (candidate == PvChangeType.NONE) {
                    continue;
                }
                if ((event.getType() & candidate.getLegacyMask()) != 0) {
                    mappedTypes.add(candidate);
                    if (primary == PvChangeType.NONE) {
                        primary = candidate;
                    }
                }
            }

            if (mappedTypes.isEmpty()) {
                mappedTypes.add(PvChangeType.NONE);
                primary = PvChangeType.NONE;
            }

            ProcessVar source = event.getSource() instanceof ProcessVar
                ? (ProcessVar) event.getSource()
                : null;

            return new PvChange(
                source,
                event.getKey(),
                event.getValue(),
                Collections.unmodifiableSet(mappedTypes),
                primary,
                event.isChildEvent(),
                event.getTime(),
                fullMask
            );
        }

        public ProcessVar getSource() {
            return source;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Set<PvChangeType> getTypes() {
            return types;
        }

        public PvChangeType getPrimaryType() {
            return primaryType;
        }

        public boolean includes(PvChangeType type) {
            return types.contains(type);
        }

        public boolean isChildChange() {
            return childChange;
        }

        public long getTimestamp() {
            return timestamp;
        }

        int getLegacyMask() {
            return legacyMask;
        }

        public PvChangeEvent toLegacyEvent() {
            ProcessVar eventSource = source != null ? source : new ProcessVar();
            PvChangeEvent event = new PvChangeEvent(
                eventSource,
                key,
                value,
                legacyMask
            );
            event.setTime(timestamp);
            return event;
        }
    }

    /**
     * Utility for clamping PV values within a configured range.
     */
    public static class PvLimits {
        /** result codes */
        /** value is within specified range */
        private static final byte RC_WITHIN_RANGE = 0x00;
        /** value is above specified range */
        private static final byte RC_ABOVE_RANGE = 0x01;
        /** value is below specified range */
        private static final byte RC_BELOW_RANGE = 0x02;

        /** minimum limit for range check */
        @SuppressWarnings("rawtypes")
        private Comparable minValue;
        /** maximum limit for range check */
        @SuppressWarnings("rawtypes")
        private Comparable maxValue;

        public PvLimits() {
        }

        @SuppressWarnings("rawtypes")
        public PvLimits(Comparable minVal, Comparable maxVal) {
            minValue = minVal;
            maxValue = maxVal;
        }

        @SuppressWarnings("rawtypes")
        public Comparable getMinValue() {
            return this.minValue;
        }

        @SuppressWarnings("rawtypes")
        public void setMinValue(Comparable minValue) {
            this.minValue = minValue;
        }

        @SuppressWarnings("rawtypes")
        public Comparable getMaxValue() {
            return this.maxValue;
        }

        @SuppressWarnings("rawtypes")
        public void setMaxValue(Comparable maxValue) {
            this.maxValue = maxValue;
        }

        private static byte checkRange(Object value, Comparable<Object> minLimit,
                                       Comparable<Object> maxLimit) {
            byte retVal = RC_WITHIN_RANGE;
            if (minLimit != null && minLimit.compareTo(value) > 0)
                retVal |= RC_BELOW_RANGE;
            if (maxLimit != null && maxLimit.compareTo(value) < 0)
                retVal |= RC_ABOVE_RANGE;
            return (retVal);
        }

        @SuppressWarnings("unchecked")
        public byte checkRange(Object value) {
            return (checkRange(value, minValue, maxValue));
        }

        @SuppressWarnings("unchecked")
        public Object limitedValue(Object value) {
            Object result;
            switch (checkRange(value, minValue, maxValue)) {
                case RC_BELOW_RANGE:
                    result = minValue;
                    break;

                case RC_ABOVE_RANGE:
                    result = maxValue;
                    break;

                default:
                    result = value;
            }
            return result;
        }
    }

    /**
     * Core mutable container for process variable attributes.
     * <p>
     * NOTE: This class intentionally keeps raw-map semantics because large
     * portions of the codebase depend on that behaviour. When we migrate to
     * generics, this is the first place to revisit.
     */
    @SuppressWarnings("rawtypes")
    public static class ProcessVar
        extends HashMap
        implements PvChangeListener, Serializable {

        private static final long serialVersionUID = 7072161686290674442L;
        /** name of key attribute */
        private Object KeyAttribute = null;
        /** default key attribute name */
        static final String DEF_KEYNAME = "key";
        /** time of last change * */
        private long lastChange = 0;
        /** type of last change * */
        private int lastChangeType = PvChangeEvent.PV_ADDED;
        /** default change action */
        int defaultAction = PvChangeEvent.PV_NOACTION;
        /** flag if to allow ChangeEvents to be fired */
        boolean allowEvents = false;
        /** list of process var change listeners */
        private transient Map<PvChangeListener, Integer> PvChangeListeners = new HashMap<>();
        /** Map of attribute changes */
        private final Map<Object, PvChangeEvent> changes = new HashMap<>();
        /** Lock guarding access to internal state. */
        private final transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        /** The logger object */
        public static final Logger log = Logger.getLogger(ProcessVar.class.getPackage().getName());

        public ProcessVar() {
            clear();
        }

        public ProcessVar(int initialSize) {
            super(initialSize);
            clear();
        }

        /** construct with a existing Map */
        public ProcessVar(Map map) {
            if (map != null) {
                putAll(map);
            }
        }

        @SuppressWarnings("unchecked")
        /**
         * Merge a map of attributes into the PV while issuing a single change event.
         */
        public void putAll(Map map, int action, boolean allowChildEvents) {
            Object[] changedValues = map.values().toArray();
            lock.writeLock().lock();
            try {
                boolean oldAllowEvents = allowEvents;
                allowEvents = allowChildEvents;
                int oldAction = defaultAction;
                defaultAction = action;
                super.putAll(map);
                defaultAction = oldAction;
                allowEvents = oldAllowEvents;
            } finally {
                lock.writeLock().unlock();
            }
            firePvChanged(new PvChangeEvent(this, getKeyAttribute(), changedValues, action));
        }

        private void putAll(Map map, int action) {
            putAll(map, action, true);
        }

        @Override
        public void putAll(Map map) {
            putAll(map, defaultAction);
        }

        @Override
        public synchronized void pvChanged(PvChangeEvent event) {
            log.finer(toString() + ":Child PvChange:" + event.toString());
            firePvChanged(new PvChangeEvent(this,
                ((ProcessVar) event.getSource()).getKeyValue(),
                event.getSource(),
                event.getType() | PvChangeEvent.PV_CHILDCHANGE));
        }

        @Override
        public String toString() {
            return (getClass().getName() + "[" + getKeyValue() + "]");
        }

        @SuppressWarnings("unchecked")
        /**
         * Insert or replace an attribute and emit a change event with the supplied action.
         */
        public Object put(Object key, Object value, int action) {
            Object oldvalue;
            int resultingAction = action;

            lock.writeLock().lock();
            try {
                if (value instanceof ProcessVar) {
                    oldvalue = super.get(key);
                    if (oldvalue instanceof ProcessVar) {
                        ((HashMap) oldvalue).putAll((Map) value);
                    } else {
                        oldvalue = super.put(key, value);
                    }
                } else {
                    oldvalue = super.put(key, value);
                }

                if (oldvalue == null) {
                    if (value != null) {
                        resultingAction |= PvChangeEvent.PV_ADDED;
                        if (value instanceof ProcessVar) {
                            ((ProcessVar) value).addPvChangeListener(this);
                        }
                    }
                } else {
                    if (!oldvalue.equals(value)) {
                        resultingAction |= PvChangeEvent.PV_MODIFIED;
                    } else {
                        PvChangeEvent lstChange = changes.get(key);
                        if (lstChange != null && (lstChange.getType() & PvChangeEvent.PV_MANUAL_MOD) != 0) {
                            resultingAction |= PvChangeEvent.PV_CONFIRMED;
                        }
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }

            firePvChanged(new PvChangeEvent(this, key, value, resultingAction));
            return oldvalue;
        }

        @Override
        public Object put(Object key, Object value) {
            int action = containsKey(key) ? defaultAction : PvChangeEvent.PV_ADDED;
            return put(key, value, action);
        }

        @Override
        public synchronized Object get(Object key) {
            return (super.get(key));
        }

        synchronized int getAsInt(Object key) {
            int result = 0;
            Object val = get(key);
            try {
                if (val != null) {
                    result = Integer.valueOf(val.toString()).intValue();
                }
            } catch (NumberFormatException e) {
                // Intentionally do nothing
            }
            return (result);
        }

        synchronized void putAsInt(Object key, int value) {
            put(key, Integer.valueOf(value));
        }

        @Override
        public synchronized Object remove(Object key) {
            Object result = super.remove(key);

            if (result != null) {
                firePvChanged(new PvChangeEvent(this, key, null, PvChangeEvent.PV_DELETED));
            }

            if (result instanceof ProcessVar) {
                ((ProcessVar) result).removePvChangeListener(this);
            }

            return (result);
        }

        @Override
        /**
         * Clear all attributes and notify listeners that the PV was reset.
         */
        public synchronized void clear() {
            super.clear();
            firePvChanged(new PvChangeEvent(this, null, null, PvChangeEvent.PV_CLEARED));
        }

        /** get object/name of key attribute */
        public Object getKeyAttribute() {
            return (KeyAttribute != null ? KeyAttribute : DEF_KEYNAME);
        }

        /** set object/name of key attribute */
        public void setKeyAttribute(Object newKeyAttribute) {
            KeyAttribute = newKeyAttribute;
        }

        /** get value of key attribute */
        public Object getKeyValue() {
            return (get(getKeyAttribute()));
        }

        /** set value of key attribute */
        public void setKeyValue(Object newKeyValue) {
            put(getKeyAttribute(), newKeyValue);
        }

        /**
         * ensure there is a list of PvChangeListeners
         * * it may be null, if PV has been de-serialized
         */
        private void ensurePvChangeListeners() {
            if (PvChangeListeners == null)
                PvChangeListeners = new HashMap<PvChangeListener, Integer>();
        }
        /**
         * Handling for list of PvChangeListeners
         */
        /** remove listener for Pv changes */
        public synchronized void removePvChangeListener(PvChangeListener l) {
            ensurePvChangeListeners();
            PvChangeListeners.remove(l);
            allowEvents = !PvChangeListeners.isEmpty();
            log.finer("-PvListener:" + toString() + "->" + String.valueOf(l));
        }

        /**
         * Register a listener for a subset of change events.
         */
        public synchronized void addPvChangeListener(PvChangeListener l, int eventMask) {
            ensurePvChangeListeners();
            PvChangeListeners.put(l, Integer.valueOf(eventMask));
            allowEvents = true;
            log.finer("+PvListener:" + toString() + "->" + String.valueOf(l));
        }

        /**
         * add listener for Pv changes
         *
         * @param l event listener to be registered
         */
        public synchronized void addPvChangeListener(PvChangeListener l) {
            addPvChangeListener(l, PvChangeEvent.PV_ALLEVENTS);
        }

        /**
         * Dispatch a change event to all registered listeners.
         */
        public synchronized void firePvChanged(PvChangeEvent e) {
            if (allowEvents && e.getType() != PvChangeEvent.PV_NOACTION) {
                log.finer("PvChange:" + e.toString());

                Integer evtMask;
                Map.Entry curr;

                ensurePvChangeListeners();
                // loop through all registered listeners ...
                Set entries = PvChangeListeners.entrySet();
                Iterator it = entries.iterator();

                while (it.hasNext()) {
                    curr = (Map.Entry) it.next();

                    if (curr.getKey() != null && curr.getKey() != this) {
                        // check if listener wants to be notified by this event
                        evtMask = (Integer) curr.getValue();

                        if ((evtMask.intValue() & e.getType()) != 0) {
                            log.finer("Notify:" + curr);
                            ((PvChangeListener) curr.getKey()).pvChanged(e);
                        }
                    }
                }
                // set time and type of last change
                lastChange = e.getTime();
                lastChangeType = e.getType();
                changes.put(e.getKey(), e);
            }
        }

        /**
         * Getter for property values.
         *
         * @return Value of property values.
         */
        public Map getValueMap() {

            return this;
        }

        /**
         * Setter for property values.
         *
         * @param values New value of property values.
         */
        public void setValueMap(Map values) {
            putAll(values);
        }
    }

    /**
     * Convenience subclass that exposes typed field access by index.
     */
    public static abstract class IndexedProcessVar extends ProcessVar {

        private static final long serialVersionUID = 8478458496218575203L;

        /** return all available field names */
        public abstract String[] getFields();

        protected IndexedProcessVar() {
            String flds[] = getFields();
            for (int i = 0; i < flds.length; i++) {
                put(flds[i], null);
            }
        }

        /** indexed get for specified field id */
        public Object get(int fieldID) {
            return (get(getFields()[fieldID]));
        }

        /** indexed put for specified field id */
        public void put(int fieldID, Object newValue) {
            put(getFields()[fieldID], newValue);
        }

        /**
         * get attribute of selected key
         * overridden method to allow synchronized access
         *
         * @param fieldIndex index to key of attribute
         * @return value of attribute
         */
        public int getAsInt(int fieldIndex) {
            return (getAsInt(getFields()[fieldIndex]));
        }

        /**
         * set attribute of selected key to selected value
         * overridden method to allow notification of process var changes
         *
         * @param fieldIndex index to key of attribute
         * @param value      value of attribute
         */
        public void putAsInt(int fieldIndex, int value) {
            putAsInt(getFields()[fieldIndex], value);
        }

        /** indexed put for specified field id */
        public Object remove(int fieldID) {
            return (remove(getFields()[fieldID]));
        }
    }

    /**
     * Collection of {@link ProcessVar} instances keyed by a primary attribute.
     */
    public static class PvList extends ProcessVar
        implements DataMapHandler {

        private static final long serialVersionUID = -4024558082429586661L;

        public PvList() {
        }

        public PvList(Object key) {
            super.setKeyAttribute(key);
        }

        @SuppressWarnings("rawtypes")
        /**
         * Merge a raw attribute map into the list, instantiating child PVs on demand.
         */
        private synchronized Object handleData(Map data, int action, boolean allowChildEvents) {
            Object result = null;


            if (data.containsKey(getKeyAttribute())) {
                // remember flag for event creation
                boolean oldAllowEvents = allowEvents;
                // set flag for event creation
                allowEvents = allowChildEvents;
                ProcessVar dataset = (ProcessVar) get(data.get(getKeyAttribute()));
                if (dataset == null) {
                    dataset = new ProcessVar();
                    dataset.setKeyAttribute(getKeyAttribute());
                }
                dataset.putAll(data, action, allowChildEvents);
                // restore flag for event creation
                allowEvents = oldAllowEvents;
                result = put(dataset.getKeyValue(), dataset, action);
            }
            return (result);
        }

        /**
         * handle a set/map of data attributes with specified notification action
         *
         * @param data   Map of new data attributes to handle
         * @param action PvChangeEvent-Action code to be used for notifications
         * @return previous value of corresponding data item
         */
        @SuppressWarnings("rawtypes")
        private synchronized Object handleData(Map data, int action) {
            return (handleData(data, action, false));
        }

        /**
         * handle a set/map of data attributes with default notification action
         *
         * @param data Map of new data attributes to handle
         * @return previous value of corresponding data item
         */
        @SuppressWarnings("rawtypes")
        public synchronized Object handleData(Map data) {
            return (handleData(data, defaultAction));
        }
    }

    /**
     * Typed wrapper around {@link ProcessVar} for incremental migration to generics.
     * Provides covariant return types while delegating to the legacy implementation.
     */
    public static class TypedProcessVar<K, V> extends ProcessVar {

        public TypedProcessVar() {
            super();
        }

        public TypedProcessVar(int initialCapacity) {
            super(initialCapacity);
        }

        @SuppressWarnings("unchecked")
        public synchronized V putTyped(K key, V value) {
            return (V) super.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public synchronized V getTyped(Object key) {
            return (V) super.get(key);
        }

        @SuppressWarnings("unchecked")
        public synchronized V removeTyped(Object key) {
            return (V) super.remove(key);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public synchronized void putAllTyped(Map<? extends K, ? extends V> map) {
            super.putAll((Map) map);
        }

        @SuppressWarnings("unchecked")
        public synchronized Set<Map.Entry<K, V>> entrySetTyped() {
            return (Set) super.entrySet();
        }
    }

    /**
     * Typed wrapper around {@link PvList} for incremental migration to generics.
     * Keeps raw backing implementation while exposing typed helpers.
     */
    public static class TypedPvList<K, PV extends ProcessVar> extends PvList {

        public TypedPvList() {
            super();
        }

        public TypedPvList(Object key) {
            super(key);
        }

        public PV putTyped(K key, PV value) {
            @SuppressWarnings("unchecked")
            PV previous = (PV) super.put(key, value);
            return previous;
        }

        @SuppressWarnings("unchecked")
        public PV getTyped(Object key) {
            return (PV) super.get(key);
        }

        @SuppressWarnings("unchecked")
        public Set<Map.Entry<K, PV>> entrySetTyped() {
            return (Set) super.entrySet();
        }
    }
}
