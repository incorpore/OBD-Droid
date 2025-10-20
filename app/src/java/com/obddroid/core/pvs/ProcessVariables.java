package com.obddroid.core.pvs;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.EventListener;
import java.util.EventObject;
import java.util.EnumSet;
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
     * Change notification emitted by {@link ProcessVar} instances.
     */
    public static class PvChangeEvent extends EventObject {

        private static final long serialVersionUID = 4378855847270229897L;

        /**
         * High-level change semantics for a PV mutation. These augment the legacy bitmask to
         * provide a richer API to callers.
         */
        public enum ChangeKind {
            ADDED(PV_ADDED),
            DELETED(PV_DELETED),
            MODIFIED(PV_MODIFIED),
            CONFIRMED(PV_CONFIRMED),
            MANUAL_MODIFIED(PV_MANUAL_MOD),
            CLEARED(PV_CLEARED),
            ERROR(PV_ERROR),
            ELIMINATED(PV_ELIMINATED),
            CHILD_CHANGED(PV_CHILDCHANGE);

            private final int bitmask;

            ChangeKind(int bitmask) {
                this.bitmask = bitmask;
            }

            int bitmask() {
                return bitmask;
            }
        }

        public static final int PV_NOACTION = 0x00;             /**< NO specified PV action */
        public static final int PV_ADDED = 0x01;                 /**< new Process var was added */
        public static final int PV_DELETED = 0x02;               /**< process var was deleted */
        public static final int PV_MODIFIED = 0x04;              /**< process var was modified */
        public static final int PV_CONFIRMED = 0x08;             /**< process var was confirmed */
        public static final int PV_MANUAL_MOD = 0x10;            /**< process var was modified manually */
        public static final int PV_CLEARED = 0x20;               /**< process var was cleared */
        public static final int PV_ERROR = 0x40;                 /**< process var has an error */
        public static final int PV_ELIMINATED = 0x80;            /**< process var got eliminated */
        public static final int PV_CHILDCHANGE = 0x8000000;      /**< child process var change */
        private static final int PV_ALLACTIONS = ~PV_CHILDCHANGE; /**< mask for all actions */
        public static final int PV_ALLEVENTS = 0xFFFFFFFF;       /**< mask for all change types */
        private static final ChangeKind[] PRIORITY_ORDER = {
            ChangeKind.ADDED,
            ChangeKind.MODIFIED,
            ChangeKind.DELETED,
            ChangeKind.CLEARED,
            ChangeKind.CONFIRMED,
            ChangeKind.MANUAL_MODIFIED,
            ChangeKind.ERROR,
            ChangeKind.ELIMINATED
        };

        private int type = PV_MODIFIED;
        private Object key = ProcessVar.DEF_KEYNAME;
        private Object value = ProcessVar.DEF_KEYNAME;
        private long time = System.currentTimeMillis();
        private EnumSet<ChangeKind> changeKinds = EnumSet.noneOf(ChangeKind.class);

        public PvChangeEvent(Object source, Object key, Object value, int type) {
            super(source);
            setType(type);
            setKey(key);
            setValue(value);
        }

        public int getType() {
            return (type & PV_ALLACTIONS);
        }

        /**
         * Returns the change kinds represented by this event as an immutable snapshot.
         */
        public EnumSet<ChangeKind> getChangeKinds() {
            return changeKinds.isEmpty() ? EnumSet.noneOf(ChangeKind.class) : changeKinds.clone();
        }

        /** Convenience predicate for checking a specific change kind. */
        public boolean hasChange(ChangeKind kind) {
            return changeKinds.contains(kind);
        }

        /** Indicates whether the event contains no actionable change bits. */
        public boolean isNoop() {
            return changeKinds.isEmpty();
        }

        /**
         * Returns the highest-priority change kind for this event, or {@code null} if none.
         */
        public ChangeKind primaryChange() {
            for (ChangeKind kind : PRIORITY_ORDER) {
                if (changeKinds.contains(kind)) {
                    return kind;
                }
            }
            return changeKinds.contains(ChangeKind.CHILD_CHANGED) ? ChangeKind.CHILD_CHANGED : null;
        }

        /** Converts the provided change kinds into the legacy bitmask representation. */
        public static int toBitmask(EnumSet<ChangeKind> kinds) {
            if (kinds == null || kinds.isEmpty()) {
                return PV_NOACTION;
            }
            int mask = PV_NOACTION;
            for (ChangeKind kind : kinds) {
                mask |= kind.bitmask();
            }
            return mask;
        }

        /** Creates a snapshot {@link EnumSet} from a legacy bitmask. */
        public static EnumSet<ChangeKind> fromBitmask(int mask) {
            EnumSet<ChangeKind> kinds = EnumSet.noneOf(ChangeKind.class);
            for (ChangeKind kind : ChangeKind.values()) {
                if ((mask & kind.bitmask()) != 0) {
                    kinds.add(kind);
                }
            }
            return kinds;
        }

        public boolean isChildEvent() {
            return (type & PV_CHILDCHANGE) != 0;
        }

        private void setType(int newType) {
            type = newType;
            changeKinds = fromBitmask(newType & PV_ALLEVENTS);
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
                        if (lstChange != null && lstChange.hasChange(PvChangeEvent.ChangeKind.MANUAL_MODIFIED)) {
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
        public Object get(Object key) {
            lock.readLock().lock();
            try {
                return super.get(key);
            } finally {
                lock.readLock().unlock();
            }
        }

        int getAsInt(Object key) {
            lock.readLock().lock();
            try {
                int result = 0;
                Object val = super.get(key);
                if (val != null) {
                    try {
                        result = Integer.parseInt(val.toString());
                    } catch (NumberFormatException e) {
                        // Intentionally ignore malformed numbers and return 0.
                    }
                }
                return result;
            } finally {
                lock.readLock().unlock();
            }
        }

        void putAsInt(Object key, int value) {
            put(key, Integer.valueOf(value));
        }

        @Override
        public Object remove(Object key) {
            Object result;
            boolean child = false;
            lock.writeLock().lock();
            try {
                result = super.remove(key);
                if (result instanceof ProcessVar) {
                    child = true;
                }
            } finally {
                lock.writeLock().unlock();
            }

            if (result != null) {
                firePvChanged(new PvChangeEvent(this, key, null, PvChangeEvent.PV_DELETED));
                if (child) {
                    ((ProcessVar) result).removePvChangeListener(this);
                }
            }

            return result;
        }

        @Override
        /**
         * Clear all attributes and notify listeners that the PV was reset.
         */
        public void clear() {
            lock.writeLock().lock();
            try {
                super.clear();
            } finally {
                lock.writeLock().unlock();
            }
            firePvChanged(new PvChangeEvent(this, null, null, PvChangeEvent.PV_CLEARED));
        }

        /** get object/name of key attribute */
        public Object getKeyAttribute() {
            lock.readLock().lock();
            try {
                return (KeyAttribute != null ? KeyAttribute : DEF_KEYNAME);
            } finally {
                lock.readLock().unlock();
            }
        }

        /** set object/name of key attribute */
        public void setKeyAttribute(Object newKeyAttribute) {
            lock.writeLock().lock();
            try {
                KeyAttribute = newKeyAttribute;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /** get value of key attribute */
        public Object getKeyValue() {
            return get(getKeyAttribute());
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
        public void removePvChangeListener(PvChangeListener l) {
            lock.writeLock().lock();
            try {
                ensurePvChangeListeners();
                PvChangeListeners.remove(l);
                allowEvents = !PvChangeListeners.isEmpty();
                log.finer("-PvListener:" + toString() + "->" + String.valueOf(l));
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Register a listener for a subset of change events.
         */
        public void addPvChangeListener(PvChangeListener l, int eventMask) {
            lock.writeLock().lock();
            try {
                ensurePvChangeListeners();
                PvChangeListeners.put(l, Integer.valueOf(eventMask));
                allowEvents = true;
                log.finer("+PvListener:" + toString() + "->" + String.valueOf(l));
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Register a listener using the richer {@link ChangeKind} metadata instead of raw bitmasks.
         */
        public void addPvChangeListener(PvChangeListener l, EnumSet<PvChangeEvent.ChangeKind> kinds) {
            int mask = PvChangeEvent.toBitmask(kinds);
            addPvChangeListener(l, mask);
        }

        /**
         * Convenience overload that accepts a vararg of change kinds.
         */
        public void addPvChangeListener(PvChangeListener l, PvChangeEvent.ChangeKind... kinds) {
            EnumSet<PvChangeEvent.ChangeKind> mask = EnumSet.noneOf(PvChangeEvent.ChangeKind.class);
            if (kinds != null) {
                Collections.addAll(mask, kinds);
            }
            addPvChangeListener(l, mask);
        }

        /**
         * add listener for Pv changes
         *
         * @param l event listener to be registered
         */
        public void addPvChangeListener(PvChangeListener l) {
            addPvChangeListener(l, PvChangeEvent.PV_ALLEVENTS);
        }

        /**
         * Dispatch a change event to all registered listeners.
         */
        public void firePvChanged(PvChangeEvent e) {
            Map<PvChangeListener, Integer> snapshot;
            lock.writeLock().lock();
            try {
                if (!allowEvents || e.isNoop()) {
                    return;
                }
                ensurePvChangeListeners();
                snapshot = new HashMap<>(PvChangeListeners);
            } finally {
                lock.writeLock().unlock();
            }

            log.finer("PvChange:" + e.toString());

            for (Map.Entry<PvChangeListener, Integer> entry : snapshot.entrySet()) {
                PvChangeListener listener = entry.getKey();
                if (listener != null && listener != this) {
                    Integer evtMask = entry.getValue();
                    if (evtMask != null && (evtMask.intValue() & e.getType()) != 0) {
                        try {
                            listener.pvChanged(e);
                        } catch (Exception listenerEx) {
                            log.warning("Listener threw exception: " + listenerEx.getMessage());
                        }
                    }
                }
            }

            lock.writeLock().lock();
            try {
                lastChange = e.getTime();
                lastChangeType = e.getType();
                changes.put(e.getKey(), e);
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Getter for property values.
         *
         * @return Value of property values.
         */
        @SuppressWarnings("unchecked")
        public Map getValueMap() {
            lock.readLock().lock();
            try {
                return new HashMap<Object, Object>(this);
            } finally {
                lock.readLock().unlock();
            }
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
        private Object handleData(Map data, int action, boolean allowChildEvents) {
            if (!data.containsKey(getKeyAttribute())) {
                return null;
            }

            Object result;
            lock.writeLock().lock();
            try {
                boolean oldAllowEvents = allowEvents;
                allowEvents = allowChildEvents;
                ProcessVar dataset = (ProcessVar) super.get(data.get(getKeyAttribute()));
                if (dataset == null) {
                    dataset = new ProcessVar();
                    dataset.setKeyAttribute(getKeyAttribute());
                }
                dataset.putAll(data, action, allowChildEvents);
                allowEvents = oldAllowEvents;
                result = put(dataset.getKeyValue(), dataset, action);
            } finally {
                lock.writeLock().unlock();
            }
            return result;
        }

        /**
         * handle a set/map of data attributes with specified notification action
         *
         * @param data   Map of new data attributes to handle
         * @param action PvChangeEvent-Action code to be used for notifications
         * @return previous value of corresponding data item
         */
        @SuppressWarnings("rawtypes")
        private Object handleData(Map data, int action) {
            return handleData(data, action, false);
        }
        /**
         * handle a set/map of data attributes with default notification action
         *
         * @param data Map of new data attributes to handle
         * @return previous value of corresponding data item
         */
        @SuppressWarnings("rawtypes")
        public Object handleData(Map data) {
            return handleData(data, defaultAction);
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
        public V putTyped(K key, V value) {
            return (V) super.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public V getTyped(Object key) {
            return (V) super.get(key);
        }

        @SuppressWarnings("unchecked")
        public V removeTyped(Object key) {
            return (V) super.remove(key);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void putAllTyped(Map<? extends K, ? extends V> map) {
            super.putAll((Map) map);
        }

        @SuppressWarnings("unchecked")
        public Set<Map.Entry<K, V>> entrySetTyped() {
            Map<Object, Object> snapshotMap = getValueMap();
            Set<Map.Entry<K, V>> snapshot = new HashSet<>();
            for (Map.Entry<Object, Object> entry : snapshotMap.entrySet()) {
                snapshot.add(new AbstractMap.SimpleEntry<>((K) entry.getKey(), (V) entry.getValue()));
            }
            return snapshot;
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
            Map<Object, Object> snapshotMap = getValueMap();
            Set<Map.Entry<K, PV>> snapshot = new HashSet<>();
            for (Map.Entry<Object, Object> entry : snapshotMap.entrySet()) {
                snapshot.add(new AbstractMap.SimpleEntry<>((K) entry.getKey(), (PV) entry.getValue()));
            }
            return snapshot;
        }
    }
}
