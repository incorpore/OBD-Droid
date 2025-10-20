package com.obddroid.core.ecu;

import android.content.Context;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DtcCatalogProviderTest {

    private static final class StubCatalog implements DtcCatalog {
        @Override
        public EcuCodeItem lookup(Number value) {
            return null;
        }

        @Override
        public Set<String> listCodes() {
            return Collections.emptySet();
        }
    }

    private static DtcCatalogProvider provider(BooleanSupplier toggle,
                                                BiFunction<Context, String, DtcCatalog> dbFactory,
                                                Function<String, ObdCodeList> resourceFactory) {
        return new DtcCatalogProvider(toggle, null, dbFactory, resourceFactory);
    }

    @Test
    public void returnsResourceCatalogWhenToggleDisabled() {
        AtomicBoolean databaseCalled = new AtomicBoolean(false);
        DtcCatalogProvider provider = provider(
            () -> false,
            (ctx, manufacturer) -> {
                databaseCalled.set(true);
                return new StubCatalog();
            },
            bundle -> new ObdCodeList(null) {
                @Override
                public EcuCodeItem lookup(Number value) {
                    return null;
                }
            }
        );

        DtcCatalog catalog = provider.provide(null);
        assertTrue(catalog instanceof ObdCodeList);
        assertFalse("Database factory should not be invoked", databaseCalled.get());
    }

    @Test
    public void returnsDatabaseCatalogWhenToggleEnabled() {
        StubCatalog dbCatalog = new StubCatalog();

        DtcCatalogProvider provider = provider(
            () -> true,
            (ctx, manufacturer) -> dbCatalog,
            bundle -> new ObdCodeList(null)
        );

        DtcCatalog catalog = provider.provide(null, "FORD");
        assertSame(dbCatalog, catalog);
    }
}
