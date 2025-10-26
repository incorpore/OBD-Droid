package com.obddroid.ecu;

import android.content.Context;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Provider that selects the appropriate {@link DtcCatalog} implementation
 * based on a supplied feature toggle.
 */
public final class DtcCatalogProvider {

    private final BooleanSupplier useDatabaseCatalog;
    private final String fallbackResourceBundle;
    private final BiFunction<Context, String, DtcCatalog> databaseFactory;
    private final Function<String, ObdCodeList> resourceFactory;

    public DtcCatalogProvider(BooleanSupplier useDatabaseCatalog) {
        this(useDatabaseCatalog, "standard.codes");
    }

    public DtcCatalogProvider(BooleanSupplier useDatabaseCatalog, String fallbackResourceBundle) {
        this(
            useDatabaseCatalog,
            fallbackResourceBundle,
            DTCDatabaseCodeList::new,
            ObdCodeList::new
        );
    }

    DtcCatalogProvider(
        BooleanSupplier useDatabaseCatalog,
        String fallbackResourceBundle,
        BiFunction<Context, String, DtcCatalog> databaseFactory,
        Function<String, ObdCodeList> resourceFactory
    ) {
        this.useDatabaseCatalog = Objects.requireNonNull(useDatabaseCatalog, "useDatabaseCatalog");
        this.fallbackResourceBundle = fallbackResourceBundle;
        this.databaseFactory = Objects.requireNonNull(databaseFactory, "databaseFactory");
        this.resourceFactory = Objects.requireNonNull(resourceFactory, "resourceFactory");
    }

    public DtcCatalog provide(Context context) {
        return provide(context, null);
    }

    public DtcCatalog provide(Context context, String manufacturer) {
        if (useDatabaseCatalog.getAsBoolean()) {
            return databaseFactory.apply(context, manufacturer);
        }
        return resourceFactory.apply(fallbackResourceBundle);
    }
}
