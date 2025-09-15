package com.obddroid.ecu.gui.androbd;

import com.obddroid.ecu.EcuDataPv;

/**
 * Process variable for plugin data
 * - key attribute is mnemonic
 * - toString returns key value
 */
public class PluginDataPv extends EcuDataPv
{
    PluginDataPv()
    {
        super();
        this.setKeyAttribute(FID_MNEMONIC);
    }

    @Override
    public String toString()
    {
        return getKeyValue().toString();
    }
}
