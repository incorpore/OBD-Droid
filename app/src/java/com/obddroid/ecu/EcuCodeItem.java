package com.obddroid.ecu;

import com.obddroid.common.ProcessVariables.IndexedProcessVar;

/**
 * ECU fault code
 *

 */
public class EcuCodeItem extends IndexedProcessVar
  implements Comparable<EcuCodeItem>
{
  /** unique class id*/
  public static final long  serialVersionUID = -1;
  // Field IDs
  public static final int FID_CODE     = 0;
  public static final int FID_DESCRIPT = 1;
  public static final int FID_STATUS   = 2;

  public static final String[] FIELDS =
  {
    "CODE",
    "DESCRIPTION",
    "STATUS",
  };

  EcuCodeItem()
  {
  }

 /** Creates a new instance of EcuCodeItem
   * @param code String representation of DFC
   * @param description descriptive text of DFC
   */
  public EcuCodeItem(String code, String description)
  {
    put(FID_CODE, code);
    put(FID_DESCRIPT, description);
  }

 /** Creates a new instance of ObdCodeItem
   * @param numericCode numeric code ID
   * @param description descriptive text of DFC
   */
  public EcuCodeItem(int numericCode, String description)
  {
    put(FID_CODE, String.valueOf(numericCode));
    put(FID_DESCRIPT, description);
  }

  public String[] getFields()
  {
    return ObdCodeItem.FIELDS;
  }

  /**
   * Return String representation of Code item
   * @return String representation of code
   */
  @Override
  public String toString()
  {
    return String.format("%02X.%s", get(FID_STATUS), get(FID_CODE));
  }

  /**
   * Implementation of Comparable to allow sorting
   * @param o the other object
   * @return result of comparison
   */
  public int compareTo(EcuCodeItem o)
  {
    return toString().compareTo(o.toString());
  }

}
